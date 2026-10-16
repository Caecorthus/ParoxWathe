package dev.doctor4t.wathe.client.skin;

import dev.doctor4t.wathe.Wathe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side texture pipeline for item skins: downloads the PNG referenced by a
 * {@code wathe:skin} component, caches it on disk and registers it as a GPU texture.
 * Source-agnostic — any provider can feed texture URLs; see docs/skin-system.md.
 */
public class ItemSkinTextureManager {
    private static final ItemSkinTextureManager INSTANCE = new ItemSkinTextureManager();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 只下载皮肤服务（ParoxSite）下发的贴图域名；组件内容不可信（公开服安全，见 docs/skin-system.md） */
    private static final String ALLOWED_TEXTURE_HOST = "parox.luoyuewu.com";
    /** 单张贴图字节上限：像素皮肤实际只有几 KB */
    private static final int MAX_TEXTURE_BYTES = 1024 * 1024;
    /** 解码后像素数上限（2048×2048），防「小文件大图」的解压炸弹 */
    private static final long MAX_TEXTURE_PIXELS = 2048L * 2048L;

    public enum TextureState { LOADING, READY, FAILED }

    private final ConcurrentHashMap<String, TextureState> textureStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Identifier> registeredTextures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ItemSkinQuadGenerator.SkinQuadData> quadCache = new ConcurrentHashMap<>();
    private Path cacheDir;

    private ItemSkinTextureManager() {}

    public static ItemSkinTextureManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        Path watheDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("wathe");
        cacheDir = watheDir.resolve("skin_cache");
        try {
            Files.createDirectories(cacheDir);
            invalidateCacheOnVersionChange(watheDir);
        } catch (Exception e) {
            Wathe.LOGGER.warn("[SkinTexture] Failed to create cache directory: {}", e.getMessage());
        }
    }

    private void invalidateCacheOnVersionChange(Path watheDir) {
        Path versionFile = watheDir.resolve(".skin_cache_version");
        String currentVersion = Wathe.MOD_VERSION;
        try {
            if (Files.exists(versionFile)) {
                String cachedVersion = Files.readString(versionFile).trim();
                if (currentVersion.equals(cachedVersion)) {
                    return;
                }
                Wathe.LOGGER.info("[SkinTexture] Mod version changed ({} -> {}), clearing skin cache",
                        cachedVersion, currentVersion);
            } else {
                Wathe.LOGGER.info("[SkinTexture] First run, initializing skin cache version marker");
            }

            // Delete all cached skin files
            try (var files = Files.list(cacheDir)) {
                files.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        Wathe.LOGGER.warn("[SkinTexture] Failed to delete cached file {}: {}", p.getFileName(), e.getMessage());
                    }
                });
            }

            Files.writeString(versionFile, currentVersion);
        } catch (Exception e) {
            Wathe.LOGGER.warn("[SkinTexture] Failed to check/update skin cache version: {}", e.getMessage());
        }
    }

    public TextureState getState(String textureUrl) {
        return textureStates.getOrDefault(textureUrl, TextureState.LOADING);
    }

    public @Nullable Identifier getTextureId(String textureUrl) {
        if (textureStates.get(textureUrl) != TextureState.READY) return null;
        return registeredTextures.get(textureUrl);
    }

    public void ensureLoaded(String textureUrl) {
        if (textureStates.containsKey(textureUrl)) return;
        textureStates.put(textureUrl, TextureState.LOADING);

        CompletableFuture.supplyAsync(() -> {
            try {
                String hash = sha256(textureUrl);
                Path cachedFile = cacheDir.resolve(hash + ".png");

                if (Files.exists(cachedFile)) {
                    return Files.readAllBytes(cachedFile);
                }

                URI uri = URI.create(textureUrl);
                if (!ALLOWED_TEXTURE_HOST.equalsIgnoreCase(uri.getHost())) {
                    throw new RuntimeException("texture host not allowed: " + uri.getHost());
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }

                // 边读边计数限流（ofByteArray 会先把整包读进内存，上限形同虚设）
                byte[] bytes = readCapped(response.body(), MAX_TEXTURE_BYTES);
                checkPixelSize(bytes);
                Files.write(cachedFile, bytes);
                return bytes;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Util.getMainWorkerExecutor()).thenAcceptAsync(bytes -> {
            try {
                InputStream stream = new ByteArrayInputStream(bytes);
                NativeImage image = NativeImage.read(stream);

                // Generate quads from the skin image before creating the GPU texture
                Sprite dummySprite = MinecraftClient.getInstance()
                        .getBakedModelManager().getMissingModel().getParticleSprite();
                ItemSkinQuadGenerator.SkinQuadData quads = ItemSkinQuadGenerator.generate(image, dummySprite);
                quadCache.put(textureUrl, quads);

                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier texId = Wathe.id("skins/" + sha256(textureUrl));

                MinecraftClient.getInstance().getTextureManager().registerTexture(texId, texture);
                registeredTextures.put(textureUrl, texId);
                textureStates.put(textureUrl, TextureState.READY);
            } catch (Exception e) {
                Wathe.LOGGER.warn("[SkinTexture] Failed to register texture for {}: {}", textureUrl, e.getMessage());
                textureStates.put(textureUrl, TextureState.FAILED);
            }
        }, MinecraftClient.getInstance()).exceptionally(e -> {
            Wathe.LOGGER.warn("[SkinTexture] Failed to download {}: {}", textureUrl, e.getMessage());
            textureStates.put(textureUrl, TextureState.FAILED);
            return null;
        });
    }

    public @Nullable ItemSkinQuadGenerator.SkinQuadData getQuads(String textureUrl) {
        return quadCache.get(textureUrl);
    }

    public void clearAll() {
        for (Identifier texId : registeredTextures.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(texId);
        }
        registeredTextures.clear();
        textureStates.clear();
        quadCache.clear();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 流式读取并限制字节数：超限直接失败，防止超大响应把内存打满 */
    private static byte[] readCapped(InputStream stream, int cap) throws IOException {
        try (stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > cap) {
                    throw new IOException("texture too large (> " + cap + " bytes)");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /** 解码前从 PNG 头读宽高：NativeImage.read 会按 w*h*4 分配内存，先挡住解压炸弹 */
    private static void checkPixelSize(byte[] bytes) throws IOException {
        if (bytes.length < 24) {
            throw new IOException("not a PNG (too short)");
        }
        // 8 字节签名 + 4 字节块长度 + "IHDR" → 宽高在 16..23（大端）
        if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new IOException("not a PNG");
        }
        long width = ((bytes[16] & 0xFFL) << 24) | ((bytes[17] & 0xFFL) << 16)
                | ((bytes[18] & 0xFFL) << 8) | (bytes[19] & 0xFFL);
        long height = ((bytes[20] & 0xFFL) << 24) | ((bytes[21] & 0xFFL) << 16)
                | ((bytes[22] & 0xFFL) << 8) | (bytes[23] & 0xFFL);
        if (width <= 0 || height <= 0 || width * height > MAX_TEXTURE_PIXELS) {
            throw new IOException("texture too large: " + width + "x" + height);
        }
    }
}

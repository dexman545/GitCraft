package de.metroite.datefetcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateFetcher {
    private static final String manifestFileName = "version_manifest.json";
    private static final String urlString = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static void fetchManifest() throws FetchException {
        try {
            URL url = URI.create(urlString).toURL();
            ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
            FileOutputStream fileOutputStream = new FileOutputStream(manifestFileName);
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0L, Long.MAX_VALUE);
            fileOutputStream.close();
        } catch (Exception e) {
            throw new FetchException(e.getMessage());
        }
    }

    public static ZonedDateTime getReleaseDate(String version) throws IOException, ParseException {
        File manifestFile = new File(manifestFileName);
        if (manifestFile.isDirectory()) {
            throw new AssertionError(String.format("%s should not be a directory!%n", manifestFile.toPath()));
        } else {
            if (!manifestFile.exists()) {
                fetchManifest();
            } else {
                BasicFileAttributes attrs = Files.readAttributes(manifestFile.toPath(), BasicFileAttributes.class);
                FileTime creationTime = attrs.lastModifiedTime();
                long then = creationTime.toMillis();
                long now = System.currentTimeMillis();
                if (now - then > 86400000) {
                    fetchManifest();
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(manifestFile);
            if (rootNode.get("versions").isArray()) {
                for (JsonNode versionNode : rootNode.get("versions")) {
                    if (version.equals(versionNode.get("id").asText())) {
                        String releaseTime = versionNode.get("releaseTime").asText();
                        return ZonedDateTime.parse(releaseTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }
                }
            }

            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: <mcVersion> <optional format: iso (default) | epoch | <SimpleDateFormat>>");
            System.exit(1);
        }

        String version = args[0];
        String format = null;
        if (args.length > 1) {
            format = args[1];
        }

        File manifestFile = new File(manifestFileName);

        try {
            ZonedDateTime releaseDate = getReleaseDate(version);
            if (releaseDate == null) {
                System.err.println("Version not found: " + version);
                System.exit(1);
            }

            String formattedDate = switch (format) {
                case "epoch" -> {
                    if (releaseDate.getOffset().getId().equals("Z")) {
                        yield String.format("%d %s", releaseDate.toInstant().getEpochSecond(), "+0000");
                    }
                    yield String.format("%d %s", releaseDate.toInstant().getEpochSecond(), releaseDate.getOffset().toString().replace(":", ""));
                }
                case null -> {
                    if (releaseDate.getOffset().getId().equals("Z")) {
                        yield releaseDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+00:00";
                    }
                    yield releaseDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
                default -> releaseDate.toString();
            };

            System.out.println(formattedDate);
        } catch (FetchException | ParseException | IOException e) {
            System.err.printf("Error with %s: %s%n", manifestFile.toPath(), e.getMessage());
            System.exit(1);
        }

    }

    private static class FetchException extends RuntimeException {
        public FetchException(String msg) {
            super(String.format("Failed to fetch %s: %s", manifestFileName, msg));
        }
    }
}

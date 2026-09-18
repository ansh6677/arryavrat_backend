package com.aryavart.dairy.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Product photos uploaded from the management panel.
 *
 * They live in GridFS rather than on disk because the backend runs on a host
 * with an ephemeral filesystem — anything written beside the jar disappears on
 * the next deploy, taking the shop's photos with it. Mongo is the only storage
 * the app already owns that survives a restart.
 *
 * Every upload is re-encoded before it is stored: a photo straight off a phone
 * is 3–8 MB and 4000 px wide, which is heavier than the entire rest of the
 * page. Capping the long edge at {@value #MAX_EDGE} px keeps a product card
 * sharp on a laptop and drops a typical upload to a couple of hundred KB.
 */
@Service
public class ImageStoreService {

    /** Long edge of the stored image, in pixels. */
    private static final int MAX_EDGE = 1600;

    /** JPEG quality for the re-encode — high enough that food still looks good. */
    private static final float QUALITY = 0.85f;

    /** What the browser is allowed to send. */
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private final GridFsOperations gridFs;

    public ImageStoreService(GridFsOperations gridFs) {
        this.gridFs = gridFs;
    }

    /** @return the new image's id, to be served back as /api/public/images/{id} */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a photo to upload.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG or WebP photos can be uploaded.");
        }

        try {
            byte[] raw = file.getBytes();
            byte[] body = raw;
            String stored = type;

            // WebP has no ImageIO reader in a stock JDK, so it is kept as sent.
            // Everything else is normalised to a modest JPEG.
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
            if (source != null) {
                body = toJpeg(shrink(source));
                stored = MediaType.IMAGE_JPEG_VALUE;
            }

            String name = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
            ObjectId id = gridFs.store(new ByteArrayInputStream(body), name, stored);
            return id.toHexString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That photo could not be read.");
        }
    }

    /** The stored bytes, or empty when the id is unknown or malformed. */
    public Loaded load(String id) {
        ObjectId oid;
        try {
            oid = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        GridFSFile file = gridFs.findOne(query(where("_id").is(oid)));
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        try {
            GridFsResource res = gridFs.getResource(file);
            try (InputStream in = res.getInputStream()) {
                return new Loaded(in.readAllBytes(), contentTypeOf(res));
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
    }

    public void delete(String id) {
        try {
            gridFs.delete(query(where("_id").is(new ObjectId(id))));
        } catch (IllegalArgumentException ignored) {
            // A bad id has nothing to delete; the caller asked for it to be gone
            // and it is gone, so this is not worth an error.
        }
    }

    public record Loaded(byte[] bytes, String contentType) {
    }

    /**
     * GridFS throws rather than returning null when a file was stored without a
     * content type — possible for anything this service did not write — so the
     * lookup is guarded and falls back to JPEG.
     */
    private static String contentTypeOf(GridFsResource res) {
        try {
            String type = res.getContentType();
            return (type == null || type.isBlank()) ? MediaType.IMAGE_JPEG_VALUE : type;
        } catch (RuntimeException e) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
    }

    /** Scales down to {@link #MAX_EDGE}; a smaller photo is left alone. */
    private static BufferedImage shrink(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * factor));
        int th = Math.max(1, (int) Math.round(h * factor));

        // Drawn onto white rather than kept as ARGB: the target is JPEG, which
        // has no alpha, and a transparent PNG would otherwise come out black.
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, tw, th);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src.getScaledInstance(tw, th, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer available");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

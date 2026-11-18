/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Utility methods to build RFC 6266 / RFC 5987 compliant {@code Content-Disposition}
 * header values for filenames containing non-ASCII characters.
 * <p>
 * Plain {@code filename="..."} parameters are restricted to ISO-8859-1 by several
 * HTTP intermediaries (notably Amazon S3's {@code response-content-disposition}
 * presigned URL override, which rejects the request with
 * {@code HTTP 400 InvalidArgument} when the value contains characters outside
 * Latin-1, e.g. Cyrillic, Greek, Korean, or Unicode punctuation such as U+2010).
 * <p>
 * The recommended fix is to always provide both parameters:
 * <pre>
 * attachment; filename="asciiFallback.pdf"; filename*=UTF-8''percent%20encoded.pdf
 * </pre>
 * Compliant clients use {@code filename*} (UTF-8, fully supports any Unicode
 * character); older/non-compliant clients fall back to the ASCII-only
 * {@code filename}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public final class ContentDispositionUtils {

    private ContentDispositionUtils() { }

    /**
     * Builds a full {@code Content-Disposition} header value for the given disposition
     * type (e.g. {@code attachment} or {@code inline}) and filename, encoding the
     * filename according to RFC 6266 / RFC 5987 so it is safe for any HTTP header
     * or query-string context, including S3 presigned URL response overrides.
     *
     * @param dispositionType the disposition type, e.g. {@code attachment} or {@code inline}
     * @param filename        the original (possibly non-ASCII) filename
     * @return the full header value, e.g.
     *         {@code attachment; filename="file.pdf"; filename*=UTF-8''file.pdf}
     */
    public static String buildContentDisposition(String dispositionType, String filename) {
        String fallbackAsciiName = createFallbackAsciiName(filename);
        String encodedUtf8Name = createEncodedUtf8Name(filename);
        return String.format(
            "%s; filename=\"%s\"; filename*=UTF-8''%s",
            dispositionType,
            fallbackAsciiName,
            encodedUtf8Name
        );
    }

    /**
     * Creates a safe ASCII-only fallback filename by removing diacritics (accents)
     * and replacing any remaining non-ASCII characters.
     * E.g., "ä-ö-é.pdf" becomes "a-o-e.pdf".
     *
     * @param originalFilename the original filename
     * @return a string containing only ASCII characters
     */
    public static String createFallbackAsciiName(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String normalized = Normalizer.normalize(originalFilename, Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.replaceAll("[^\\x00-\\x7F]", "");
    }

    /**
     * Creates a percent-encoded UTF-8 filename according to RFC 5987.
     * This is for the {@code filename*} parameter.
     * E.g., "ä ö é.pdf" becomes "%C3%A4%20%C3%B6%20%C3%A9.pdf".
     *
     * @param originalFilename the original filename
     * @return a percent-encoded string
     */
    public static String createEncodedUtf8Name(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String encoded = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20");
    }
}

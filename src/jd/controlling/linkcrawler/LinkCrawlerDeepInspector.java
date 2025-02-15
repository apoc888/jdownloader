package jd.controlling.linkcrawler;

import java.util.List;

import jd.controlling.linkcrawler.LinkCrawler.LinkCrawlerGeneration;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.net.httpconnection.HTTPConnectionUtils;

public abstract class LinkCrawlerDeepInspector {
    /**
     * https://www.iana.org/assignments/media-types/media-types.xhtml
     *
     * @param urlConnection
     * @return
     */
    public boolean looksLikeDownloadableContent(final URLConnectionAdapter urlConnection) {
        if (urlConnection.getResponseCode() == 200 || urlConnection.getResponseCode() == 206) {
            final long completeContentLength = urlConnection.getCompleteContentLength();
            final String contentType = urlConnection.getHeaderField(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE);
            final boolean hasContentType = StringUtils.isNotEmpty(contentType);
            final boolean hasContentLength = StringUtils.isNotEmpty(urlConnection.getHeaderField(HTTPConstants.HEADER_RESPONSE_CONTENT_LENGTH));
            final boolean allowsByteRanges = StringUtils.contains(urlConnection.getHeaderField(HTTPConstants.HEADER_RESPONSE_ACCEPT_RANGES), "bytes");
            final String eTag = urlConnection.getHeaderField(HTTPConstants.HEADER_ETAG);
            final boolean hasEtag = StringUtils.isNotEmpty(eTag);
            final boolean hasStrongEtag = hasEtag && !eTag.matches("(?i)^\\s*W/.*");
            final String filePathName = new Regex(urlConnection.getURL().getPath(), ".*?/([^/]+)$").getMatch(0);
            final long sizeDownloadableContent;
            if (StringUtils.endsWithCaseInsensitive(filePathName, ".epub")) {
                sizeDownloadableContent = 1 * 1024 * 1024l;
            } else {
                sizeDownloadableContent = 2 * 1024 * 1024l;
            }
            if (urlConnection.isContentDisposition()) {
                final String contentDispositionHeader = urlConnection.getHeaderField(HTTPConstants.HEADER_RESPONSE_CONTENT_DISPOSITION);
                final String contentDispositionFileName = HTTPConnectionUtils.getFileNameFromDispositionHeader(contentDispositionHeader);
                final boolean inlineFlag = contentDispositionHeader.matches("(?i)^\\s*inline\\s*;?.*");
                if (inlineFlag && (contentDispositionFileName != null && contentDispositionFileName.matches("(?i)^.*\\.html?$") || (hasContentType && isTextContent(urlConnection)))) {
                    // Content-Type: text/html;
                    // Content-Disposition: inline; filename=error.html
                    return false;
                } else {
                    return true;
                }
            } else if (completeContentLength == 0) {
                return false;
            } else if (hasContentType && (!isTextContent(urlConnection) && contentType.matches("(?i)^(application|audio|video|image)/.+"))) {
                if (isOtherTextContent(urlConnection)) {
                    return false;
                } else if (looksLikeMpegURL(urlConnection)) {
                    return false;
                } else {
                    return true;
                }
            } else if (hasContentType && (!isTextContent(urlConnection) && contentType.matches("(?i)^binary/octet-stream"))) {
                return true;
            } else if (hasContentType && !isTextContent(urlConnection) && completeContentLength > 0) {
                return true;
            } else if (!hasContentType && completeContentLength > 0 && hasStrongEtag) {
                return true;
            } else if (!hasContentType && completeContentLength > 0 && allowsByteRanges) {
                // HTTP/1.1 200 OK
                // Content-Length: 1156000
                // Accept-Ranges: bytes
                return true;
            } else if (completeContentLength > sizeDownloadableContent && (!hasContentType || !isTextContent(urlConnection))) {
                return true;
            } else if (completeContentLength > sizeDownloadableContent && (allowsByteRanges || (urlConnection.getResponseCode() == 206 && urlConnection.getRange() != null))) {
                return true;
            } else if (filePathName != null && filePathName.matches("(?i).+\\.epub") && isPlainTextContent(urlConnection) && (!hasContentLength || completeContentLength > sizeDownloadableContent)) {
                return true;
            } else if (filePathName != null && filePathName.matches("(?i).+\\.srt") && isPlainTextContent(urlConnection) && (!hasContentLength || completeContentLength > 512 || hasEtag)) {
                /*
                 * Accept-Ranges: bytes
                 *
                 * Content-Type: text/plain
                 *
                 * ETag: "11968........"
                 *
                 * Content-Length: 28...
                 */
                return true;
            }
        }
        return false;
    }

    /** Use this to check for HLS content. */
    public static boolean looksLikeMpegURL(URLConnectionAdapter con) {
        return con != null && (StringUtils.equalsIgnoreCase(con.getContentType(), "application/vnd.apple.mpegurl") || StringUtils.equalsIgnoreCase(con.getContentType(), "application/x-mpegurl"));
    }

    public boolean isTextContent(final URLConnectionAdapter urlConnection) {
        return isOtherTextContent(urlConnection) || isPlainTextContent(urlConnection) || isHtmlContent(urlConnection);
    }

    public boolean isOtherTextContent(final URLConnectionAdapter urlConnection) {
        if (isHtmlContent(urlConnection)) {
            return false;
        } else {
            final String contentType = urlConnection.getContentType();
            return contentType != null && (contentType.matches("(?i)application/[^ ;]*(json|xml).*") || contentType.matches("(?i)text/xml.*"));
        }
    }

    public boolean isPlainTextContent(final URLConnectionAdapter urlConnection) {
        final String contentType = urlConnection.getContentType();
        return StringUtils.containsIgnoreCase(contentType, "text/plain");
    }

    public boolean isHtmlContent(final URLConnectionAdapter urlConnection) {
        final String contentType = urlConnection.getContentType();
        return StringUtils.containsIgnoreCase(contentType, "text/html") || StringUtils.containsIgnoreCase(contentType, "application/xhtml+xml");
    }

    public abstract List<CrawledLink> deepInspect(LinkCrawler lc, final LinkCrawlerGeneration generation, Browser br, URLConnectionAdapter urlConnection, final CrawledLink link) throws Exception;
}

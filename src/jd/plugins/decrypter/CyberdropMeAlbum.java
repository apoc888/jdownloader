//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.decrypter;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.DirectHTTP;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.net.URLHelper;
import org.jdownloader.plugins.controller.UpdateRequiredClassNotFoundException;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class CyberdropMeAlbum extends PluginForDecrypt {
    public CyberdropMeAlbum(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "cyberdrop.me", "cyberdrop.to", "cyberdrop.cc" });
        ret.add(new String[] { "bunkr.is" });// same template/system?
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            String regex = "https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/a/[A-Za-z0-9]+";
            regex += "|https?://stream\\d*\\." + buildHostsPatternPart(domains) + "/v/[^/]+\\.(?:mp4|m4v)";
            regex += "|https?://cdn\\d*\\." + buildHostsPatternPart(domains) + "/[^/]+\\.(?:mp4|m4v)";
            regex += "|https?://media-files\\d*\\." + buildHostsPatternPart(domains) + "/[^/]+\\.(?:mp4|m4v)";
            regex += "|https?://fs-\\d+\\." + buildHostsPatternPart(domains) + "/.+\\.(?:mp4|m4v|jpe?g)";
            ret.add(regex);
        }
        return ret.toArray(new String[0]);
    }

    private static final String TYPE_ALBUM       = "https?://[^/]+/a/([A-Za-z0-9]+)";
    public static final String  TYPE_STREAM      = "https?://stream(\\d*)\\.[^/]+/v/(.+\\.(?:mp4|m4v))";   // bunkr
    public static final String  TYPE_CDN         = "https?://cdn(\\d*)\\.[^/]+/(.+\\.(?:mp4|m4v))";        // bunkr
    public static final String  TYPE_FS          = "https?://fs-(\\d+)\\.[^/]+/(.+\\.(?:mp4|m4v|jpe?g))";  // cyberdrop
    public static final String  TYPE_MEDIA_FILES = "https?://media-files(\\d*)\\.[^/]+/(.+\\.(?:mp4|m4v))"; // bunkr
    private PluginForHost       plugin           = null;

    private DownloadLink add(List<DownloadLink> decryptedLinks, Set<String> dups, final String directurl, final String filename, final String filesizeBytes, final String filesize) throws Exception {
        if (dups == null || dups.add(directurl)) {
            final String correctDirectURL = correctDirecturl(directurl);
            final DownloadLink dl;
            if (correctDirectURL != null) {
                dl = this.createDownloadlink(correctDirectURL);
            } else {
                dl = this.createDownloadlink(directurl);
            }
            dl.setProperty(DirectHTTP.PROPERTY_RATE_LIMIT, 500);
            dl.setProperty(DirectHTTP.PROPERTY_MAX_CONCURRENT, 1);
            if (getHost().equals("bunkr.is")) {
                dl.setProperty(DirectHTTP.FORCE_NOCHUNKS, true);
            }
            if (plugin == null) {
                try {
                    final LazyHostPlugin lazyHostPlugin = HostPluginController.getInstance().get(getHost());
                    if (lazyHostPlugin == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    plugin = lazyHostPlugin.getPrototype(null, false);
                } catch (UpdateRequiredClassNotFoundException e) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, null, e);
                }
            }
            if (correctDirectURL != null) {
                // direct assign the dedicated hoster plugin because it does not have any URL regex
                dl.setDefaultPlugin(plugin);
                dl.setAvailable(true);
            }
            if (filename != null) {
                dl.setFinalFileName(filename);
            }
            if (filesizeBytes != null) {
                dl.setVerifiedFileSize(Long.parseLong(filesizeBytes));
            } else if (filesize != null) {
                dl.setDownloadSize(SizeFormatter.getSize(filesize));
            }
            decryptedLinks.add(dl);
            return dl;
        } else {
            return null;
        }
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String directURL = correctDirecturl(param.getCryptedUrl());
        if (directURL != null) {
            // only if domain already contains specific server number
            add(decryptedLinks, null, param.getCryptedUrl(), null, null, null);
        } else {
            /* TYPE_ALBUM */
            br.setFollowRedirects(true);
            br.getPage(param.getCryptedUrl());
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final String albumjs = br.getRegex("const albumData\\s*=\\s*(\\{.*?\\})").getMatch(0);
            String fpName = new Regex(albumjs, "name\\s*:\\s*'([^\\']+)'").getMatch(0);
            if (fpName == null) {
                // bunkr.is
                fpName = br.getRegex("property\\s*=\\s*\"og:title\"\\s*content\\s*=\\s*\"(.*?)\"").getMatch(0);
            }
            if (fpName == null) {
                fpName = br.getRegex("<h1 id=\"title\"[^>]*title=\"([^\"]+)\"[^>]*>").getMatch(0);
                if (fpName == null) {
                    // bunkr.is
                    fpName = br.getRegex("<h1 id=\"title\"[^>]*>\\s*(.*?)\\s*<").getMatch(0);
                }
            }
            final HashSet<String> dups = new HashSet<String>();
            final String albumDescription = br.getRegex("<span id=\"description-box\"[^>]*>([^<>\"]+)</span>").getMatch(0);
            String json = br.getRegex("<script\\s*id\\s*=\\s*\"__NEXT_DATA__\"\\s*type\\s*=\\s*\"application/json\">\\s*(\\{.*?\\})\\s*</script").getMatch(0);
            if (json != null) {
                final HashMap<String, Object> map = JSonStorage.restoreFromString(json, TypeRef.HASHMAP);
                List<Map<String, Object>> files = (List<Map<String, Object>>) JavaScriptEngineFactory.walkJson(map, "props/pageProps/files");
                if (files == null) {
                    files = (List<Map<String, Object>>) JavaScriptEngineFactory.walkJson(map, "props/pageProps/album/files");
                }
                if (files == null) {
                    files = new ArrayList<Map<String, Object>>();
                }
                Map<String, Object> pagePropsFile = (Map<String, Object>) JavaScriptEngineFactory.walkJson(map, "props/pageProps/file");
                if (pagePropsFile == null) {
                    pagePropsFile = (Map<String, Object>) JavaScriptEngineFactory.walkJson(map, "props/pageProps/album/file");
                }
                if (pagePropsFile != null) {
                    files.add(pagePropsFile);
                }
                if (files != null) {
                    for (Map<String, Object> file : files) {
                        final String name = (String) file.get("name");
                        String cdn = (String) file.get("cdn");
                        if (StringUtils.isEmpty(cdn)) {
                            cdn = (String) file.get("mediafiles");
                        }
                        final String size = StringUtils.valueOfOrNull(file.get("size"));
                        if (name != null && cdn != null) {
                            final String directurl = URLHelper.parseLocation(new URL(cdn), name);
                            add(decryptedLinks, dups, directurl, name, size != null && size.matches("[0-9]+") ? size : null, size != null && !size.matches("[0-9]+") ? size : null);
                        }
                    }
                }
            }
            final String[] htmls = br.getRegex("<div class=\"image-container column\"[^>]*>(.*?)/p>\\s*</div>").getColumn(0);
            for (final String html : htmls) {
                String filename = new Regex(html, "target=\"_blank\" title=\"([^<>\"]+)\"").getMatch(0);
                if (filename == null) {
                    // bunkr.is
                    filename = new Regex(html, "<p\\s*class\\s*=\\s*\"name\"\\s*>\\s*(.*?)\\s*<").getMatch(0);
                }
                final String directurl = new Regex(html, "href=\"(https?://[^\"]+)\"").getMatch(0);
                if (directurl != null) {
                    final String filesizeBytes = new Regex(html, "class=\"(?:is-hidden)?\\s*file-size\"[^>]*>(\\d+) B").getMatch(0);
                    final String filesize = new Regex(html, "class=\"(?:is-hidden)?\\s*file-size\"[^>]*>([0-9\\.]+\\s+[MKG]B)").getMatch(0);
                    add(decryptedLinks, dups, directurl, filename, filesizeBytes, filesize);
                }
            }
            json = br.getRegex("dynamicEl\\s*:\\s*(\\[\\s*\\{.*?\\])").getMatch(0);
            if (json != null) {
                /* gallery mode only works for images */
                final List<Map<String, Object>> ressourcelist = (List<Map<String, Object>>) JavaScriptEngineFactory.jsonToJavaObject(json);
                for (final Map<String, Object> photo : ressourcelist) {
                    final String downloadUrl = (String) photo.get("downloadUrl");
                    final String subHtml = (String) photo.get("subHtml");
                    final String filesizeStr = new Regex(subHtml, "(\\d+(\\.\\d+)? [A-Za-z]{2,5})$").getMatch(0);
                    add(decryptedLinks, dups, downloadUrl, null, null, filesizeStr);
                }
            }
            if (decryptedLinks.size() == 0) {
                if (br.containsHTML(">\\s*0 files\\s*<")) {
                    return decryptedLinks;
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (fpName != null) {
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(Encoding.htmlDecode(fpName.trim()));
                fp.setAllowInheritance(param.getCryptedUrl().matches(TYPE_ALBUM));
                if (!StringUtils.isEmpty(albumDescription)) {
                    fp.setComment(albumDescription);
                }
                fp.addLinks(decryptedLinks);
            }
        }
        return decryptedLinks;
    }

    /** 2022-03-14: Especially required for bunkr.is video-URLs. */
    private String correctDirecturl(final String url) {
        if (url.matches(TYPE_STREAM)) {
            final String cdn = new Regex(url, TYPE_STREAM).getMatch(0);
            if (StringUtils.isNotEmpty(cdn)) {
                return "https://media-files" + StringUtils.valueOrEmpty(cdn) + "." + this.getHost() + "/" + new Regex(url, TYPE_STREAM).getMatch(1);
            } else {
                return null;
            }
        } else if (url.matches(TYPE_CDN)) {
            final String cdn = new Regex(url, TYPE_CDN).getMatch(0);
            if (StringUtils.isNotEmpty(cdn)) {
                return "https://media-files" + StringUtils.valueOrEmpty(cdn) + "." + this.getHost() + "/" + new Regex(url, TYPE_STREAM).getMatch(1);
            } else {
                return null;
            }
        } else if (url.matches(TYPE_FS)) {
            // cyberdrop
            return url;
        } else if (url.matches(TYPE_MEDIA_FILES)) {
            // bunkr
            return url;
        } else {
            return null;
        }
    }

    @Override
    public int getMaxConcurrentProcessingInstances() {
        return 1;
    }
}

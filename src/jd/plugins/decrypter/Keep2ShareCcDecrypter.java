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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.download.HashInfo;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public class Keep2ShareCcDecrypter extends PluginForDecrypt {
    public Keep2ShareCcDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "k2s.cc", "keep2share.cc", "k2share.cc", "keep2s.cc", "keep2.cc" });
        ret.add(new String[] { "fileboom.me", "fboom.me" });
        ret.add(new String[] { "tezfiles.com", "publish2.me" });
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
            ret.add("https?://(?:[a-z0-9\\-]+\\.)?" + buildHostsPatternPart(domains) + "/(?:folder/(?:info/)?|thumbnail/)([a-z0-9]{13,})");
        }
        return ret.toArray(new String[0]);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        /* 2020-05-13: Use the keep2share plugin for all */
        final PluginForHost plugin = getNewPluginForHostInstance("k2s.cc");
        final String fuid = new Regex(param.getCryptedUrl(), this.getSupportedLinks()).getMatch(0);
        if (fuid == null) {
            /* This should never happen */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (param.getCryptedUrl().matches("https?://[^/]+/thumbnail/.*")) {
            /* 2020-07-21: Thumbnail to video / single file --> Goes into host plugin */
            final String url = "https://" + this.getHost() + "/file/" + fuid;
            final DownloadLink dl = this.createDownloadlink(url);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        br = ((jd.plugins.hoster.Keep2ShareCc) plugin).newWebBrowser(true);
        // set cross browser support
        ((jd.plugins.hoster.K2SApi) plugin).setBrowser(br);
        ((jd.plugins.hoster.Keep2ShareCc) plugin).postPageRaw(br, "https://" + this.getHost() + "/api/v2/getfilesinfo", "{\"ids\":[\"" + fuid + "\"]}", null);
        Map<String, Object> response = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
        if (!"success".equals(response.get("status"))) {
            decryptedLinks.add(this.createOfflinelink(param.getCryptedUrl()));
            return decryptedLinks;
        }
        boolean folderHandling = false;
        FilePackage fp = null;
        final List<Map<String, Object>> files = (List<Map<String, Object>>) response.get("files");
        if (files.size() == 0) {
            logger.info("Empty object");
            decryptedLinks.add(this.createOfflinelink(param.getCryptedUrl()));
            return decryptedLinks;
        }
        if (files != null) {
            for (Map<String, Object> file : files) {
                final String id = (String) file.get("id");
                final Boolean isAvailable = (Boolean) file.get("is_available");
                if (Boolean.FALSE.equals(isAvailable)) {
                    decryptedLinks.add(createOfflinelink("https://" + this.getHost() + "/file/" + id));
                    continue;
                }
                final String name = (String) file.get("name");
                final String size = StringUtils.valueOfOrNull(file.get("size"));
                final String md5 = (String) file.get("md5");
                final String access = (String) file.get("access");
                final Boolean isFolder = (Boolean) file.get("is_folder");
                if (Boolean.FALSE.equals(isFolder)) {
                    final DownloadLink link = createDownloadlink("https://" + this.getHost() + "/file/" + id);
                    if (StringUtils.isNotEmpty(name)) {
                        link.setName(name);
                    }
                    if (StringUtils.isNotEmpty(size)) {
                        link.setVerifiedFileSize(Long.parseLong(size));
                    }
                    link.setHashInfo(HashInfo.parse(md5));
                    link.setAvailable(Boolean.TRUE.equals(isAvailable));
                    link.setProperty("access", access);
                    decryptedLinks.add(link);
                } else if (StringUtils.equals(id, fuid)) {
                    fp = FilePackage.getInstance();
                    if (StringUtils.isNotEmpty(name)) {
                        fp.setName(name);
                    }
                    folderHandling = true;
                    break;
                }
            }
        }
        if (folderHandling) {
            final Set<String> dups = new HashSet<String>();
            final int limit = 50;
            int offset = 0;
            do {
                ((jd.plugins.hoster.Keep2ShareCc) plugin).postPageRaw(br, "/getfilestatus", "{\"id\":\"" + fuid + "\",\"limit\":" + limit + ",\"offset\":" + offset + "}", null);
                response = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
                final List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("files");
                if (items.size() == 0) {
                    if (decryptedLinks.size() == 0) {
                        logger.info("Empty folder");
                        decryptedLinks.add(this.createOfflinelink(param.getCryptedUrl()));
                    }
                    break;
                }
                boolean next = false;
                if (items != null && items.size() > 0) {
                    for (Map<String, Object> file : items) {
                        final Boolean isFolder = (Boolean) file.get("is_folder");
                        final Boolean isAvailable = (Boolean) file.get("is_available");
                        final String id = (String) file.get("id");
                        if (dups.add(id)) {
                            next = true;
                        }
                        final String name = (String) file.get("name");
                        final String md5 = (String) file.get("md5");
                        final String size = file.get("size").toString();
                        if (Boolean.FALSE.equals(isFolder)) {
                            final DownloadLink link = createDownloadlink("https://" + this.getHost() + "/file/" + id);
                            if (StringUtils.isNotEmpty(name)) {
                                link.setName(name);
                            }
                            if (StringUtils.isNotEmpty(size)) {
                                link.setVerifiedFileSize(Long.parseLong(size));
                            }
                            link.setHashInfo(HashInfo.parse(md5));
                            link.setAvailable(Boolean.TRUE.equals(isAvailable));
                            decryptedLinks.add(link);
                        } else {
                            final DownloadLink link = createDownloadlink("https://" + this.getHost() + "/folder/" + id);
                            decryptedLinks.add(link);
                        }
                    }
                } else {
                    break;
                }
                offset += limit;
                if (next == false) {
                    break;
                }
            } while (!this.isAbort());
        }
        if (fp != null) {
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}

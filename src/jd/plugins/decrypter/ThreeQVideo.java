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
import java.util.List;
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.config.ThreeQVideoConfig;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DirectHTTP;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class ThreeQVideo extends PluginForDecrypt {
    public ThreeQVideo(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "playout.3qsdn.com" });
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
            ret.add("https?://" + buildHostsPatternPart(domains) + "/config/[a-f0-9\\-]+(\\?[^/]+)?");
        }
        return ret.toArray(new String[0]);
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.getHeaders().put("Accept", "*/*");
        br.getPage(param.getCryptedUrl());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Map<String, Object> root = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
        final String title = root.get("title").toString();
        final String description = (String) root.get("description");
        final String date = root.get("upload_date").toString();
        final String dateFormatted = new Regex(date, "^(\\d{4}-\\d{2}-\\d{2})").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(dateFormatted + "_" + title);
        if (!StringUtils.isEmpty(description)) {
            fp.setComment(description);
        }
        final ThreeQVideoConfig cfg = PluginJsonConfig.get(ThreeQVideoConfig.class);
        final Map<String, Object> sources = (Map<String, Object>) root.get("sources");
        final List<Map<String, Object>> sourcesProgressive = (List<Map<String, Object>>) sources.get("progressive");
        int heightMax = -1;
        DownloadLink maxQuality = null;
        for (final Map<String, Object> sourceProgressive : sourcesProgressive) {
            final String directurl = sourceProgressive.get("src").toString();
            final int height = ((Number) sourceProgressive.get("height")).intValue();
            final String filename = dateFormatted + "_" + title + "_" + height + "p.mp4";
            final DownloadLink video = this.createDownloadlink(directurl);
            video.setFinalFileName(filename);
            video.setProperty(DirectHTTP.FIXNAME, filename);
            video.setAvailable(true);
            video._setFilePackage(fp);
            if (height > heightMax) {
                heightMax = height;
                maxQuality = video;
            }
            decryptedLinks.add(video);
        }
        /* Check if user wants best quality only. */
        if (cfg.isOnlyGrabBestQuality()) {
            /* Clear list of collected items */
            decryptedLinks.clear();
            /* Add best quality only */
            decryptedLinks.add(maxQuality);
        }
        return decryptedLinks;
    }

    @Override
    public Class<? extends PluginConfigInterface> getConfigInterface() {
        return ThreeQVideoConfig.class;
    }
}

//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountRequiredException;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "bangbros.com", "mygf.com" }, urls = { "https?://members\\.bangbros\\.com/product/\\d+/movie/\\d+|https?://(?:bangbrothers\\.(?:com|net)|bangbros\\.com)/video\\d+/[a-z0-9\\-]+", "https?://members\\.mygf\\.com/product/\\d+/movie/\\d+" })
public class BangbrosCom extends PluginForDecrypt {
    public BangbrosCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static final String type_userinput_video_couldbe_trailer = ".+/video\\d+/[a-z0-9\\-]+";
    public static final String type_decrypted_zip                   = ".+\\.zip.*?";

    protected DownloadLink createDownloadlink(String url, final String fid, final String productid, final String quality) {
        final String hostpart = this.getHost().split("\\.")[0];
        url = url.replaceAll("https?://", hostpart + "decrypted://");
        final DownloadLink dl = super.createDownloadlink(url, true);
        dl.setProperty("fid", fid);
        if (quality != null) {
            dl.setProperty("quality", quality);
        }
        dl.setProperty("mainlink", br.getURL());
        return dl;
    }

    protected DownloadLink createDownloadlink(String url, final String fid) {
        return createDownloadlink(url, fid, null, null);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replace("bangbrothers.com", "bangbros.com");
        final String fid;
        final SubConfiguration cfg = SubConfiguration.getConfig(this.getHost());
        final boolean is_logged_in = getUserLogin(false);
        final boolean loginRequired = requiresAccount(parameter);
        if (!is_logged_in && loginRequired) {
            logger.info("Account required");
            throw new AccountRequiredException();
        }
        br.getPage(parameter);
        if (isOffline(this.br, parameter)) {
            final DownloadLink offline = this.createOfflinelink(parameter);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        String title = null;
        String cast_comma_separated = null;
        final String cast_html = br.getRegex("<span class=\"tag\">Cast:(.*?)</span>").getMatch(0);
        if (cast_html != null) {
            final String[] castMembers = new Regex(cast_html, "class=\"tagB\">([^<]*)<").getColumn(0);
            if (castMembers.length > 0) {
                cast_comma_separated = "";
                for (String castMember : castMembers) {
                    castMember = castMember.trim();
                    if (cast_comma_separated.length() > 0) {
                        cast_comma_separated += ",";
                    }
                    cast_comma_separated += castMember;
                }
            }
        }
        if (parameter.matches(type_userinput_video_couldbe_trailer)) {
            final String url_name = new Regex(parameter, "([a-z0-9\\-]+$)").getMatch(0);
            fid = new Regex(parameter, "/video(\\d+)").getMatch(0);
            if (!is_logged_in) {
                /* Trailer / MOCH download */
                title = br.getRegex("<div class=\"ps\\-vdoHdd\"><h1>([^<>\"]+)</h1>").getMatch(0);
                if (title == null) {
                    title = br.getRegex("<title>([^<>\"]+)</title>").getMatch(0);
                }
                if (title == null) {
                    /* Final fallback */
                    title = url_name;
                }
                title = Encoding.htmlDecode(title).trim();
                title = fid + "_" + title + ".mp4";
                final DownloadLink dl = createDownloadlink(parameter, fid);
                dl.setProperty("decryptername", title);
                decryptedLinks.add(dl);
            } else {
                /* TODO */
            }
        } else {
            final Regex finfo = new Regex(parameter, "product/(\\d+)/movie/(\\d+)");
            final String productid = finfo.getMatch(0);
            fid = finfo.getMatch(1);
            final String directurl_photos = regexZipUrl(this.br, "pictures");
            String directurl_screencaps = regexZipUrl(this.br, "screencaps");
            if (directurl_screencaps != null) {
                /* Protocol might sometimes be missing! */
                directurl_screencaps = br.getURL(directurl_screencaps).toString();
            }
            title = this.br.getRegex("class=\"vdo\\-hdd1\">([^<>\"]+)<").getMatch(0);
            if (title == null) {
                title = this.br.getRegex("class=\"desTxt\\-hed\">([^<>\"]+)<").getMatch(0);
            }
            if (title != null) {
                title = Encoding.htmlDecode(title);
                if (cast_comma_separated != null) {
                    title = cast_comma_separated + "_" + title;
                }
            } else {
                /* Fallback to id from inside url */
                title = fid;
            }
            /* 2019-01-29: Content-Servers are very slow */
            final boolean fast_linkcheck = true;
            final String[] htmls_videourls = getVideourls(this.br);
            for (final String html_videourl : htmls_videourls) {
                String videourl = getVideourlFromHtml(html_videourl);
                if (videourl == null) {
                    continue;
                }
                videourl = br.getURL(videourl).toString();
                final String quality_url = new Regex(videourl, "(\\d+p)").getMatch(0);
                if (quality_url == null || !cfg.getBooleanProperty("GRAB_" + quality_url, true)) {
                    continue;
                }
                final String ext = ".mp4";
                final DownloadLink dl = this.createDownloadlink(videourl, fid, productid, quality_url);
                dl.setForcedFileName(title + "_" + quality_url + ext);
                if (fast_linkcheck) {
                    dl.setAvailable(true);
                }
                decryptedLinks.add(dl);
            }
            if (cfg.getBooleanProperty("GRAB_photos", false) && directurl_photos != null) {
                final String quality = "pictures";
                final DownloadLink dl = this.createDownloadlink(directurl_photos, fid, productid, quality);
                dl.setForcedFileName(title + "_" + quality + ".zip");
                if (fast_linkcheck) {
                    dl.setAvailable(true);
                }
                decryptedLinks.add(dl);
            }
            if (cfg.getBooleanProperty("GRAB_screencaps", false) && directurl_screencaps != null) {
                final String quality = "screencaps";
                final DownloadLink dl = this.createDownloadlink(directurl_screencaps, fid, productid, quality);
                dl.setForcedFileName(title + "_" + quality + ".zip");
                if (fast_linkcheck) {
                    dl.setAvailable(true);
                }
                decryptedLinks.add(dl);
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title);
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }

    public static String[] getVideourls(final Browser br) {
        final String html_urltable = br.getRegex("class=\"dropM\">\\s*?<ul>(.*?)</ul>").getMatch(0);
        return html_urltable.split("<li>");
    }

    public static String getVideourlFromHtml(final String html) {
        String videourl = new Regex(html, "\"((https?:|//)[^<>\"]+)\"").getMatch(0);
        if (videourl != null) {
            videourl = Encoding.htmlDecode(videourl);
        }
        return videourl;
    }

    public static String regexZipUrl(final Browser br, final String key) {
        String zipurl = br.getRegex("([^<>\"\\']+/" + key + "/[^<>\"\\']+\\.zip[^<>\"\\']+)").getMatch(0);
        if (zipurl != null) {
            zipurl = Encoding.htmlDecode(zipurl);
        }
        return zipurl;
    }

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    private boolean getUserLogin(final boolean force) throws Exception {
        final PluginForHost hostPlugin = JDUtilities.getPluginForHost(this.getHost());
        final Account aa = AccountController.getInstance().getValidAccount(this.getHost());
        if (aa == null) {
            logger.warning("There is no account available, stopping...");
            return false;
        }
        try {
            ((jd.plugins.hoster.BangbrosCom) hostPlugin).login(this.br, aa, force);
        } catch (final PluginException e) {
            return false;
        }
        return true;
    }

    public static boolean requiresAccount(final String url) {
        return url != null && !(url.matches(type_userinput_video_couldbe_trailer) || url.matches(type_decrypted_zip));
    }

    public static boolean isOffline(final Browser br, final String url_source) {
        final boolean isOffline;
        if (url_source != null && url_source.matches(type_userinput_video_couldbe_trailer)) {
            isOffline = br.getHttpConnection().getResponseCode() == 503 || !br.getURL().contains("/video");
        } else {
            isOffline = !br.getURL().contains("/movie/") || br.getURL().contains("/library");
        }
        return isOffline;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
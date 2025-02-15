//    jDownloader - Downloadmanager
//    Copyright (C) 2014  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.HashMap;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.components.antiDDoSForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "gamemaps.com" }, urls = { "https?://(?:www\\.)?gamemaps\\.com/(?:details/|mirrors/|mirrors/mirror/(?:\\d+/)?)\\d+" })
public class GamMapCm extends antiDDoSForHost {
    private String         ddlink    = null;
    private final String   cacheLink = "cacheLink";
    private final String[] servers   = new String[] { "USA", "LONDON" };

    /**
     * @author raztoki
     */
    public GamMapCm(PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
    }

    @Override
    public void correctDownloadLink(DownloadLink link) throws PluginException {
        // get id
        final String fuid = getFUID(link);
        // set unique id
        if (fuid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Error could not determine fuid");
        } else {
            final String linkID = getHost() + "://" + fuid;
            link.setLinkID(linkID);
            // get host
            final String host = new Regex(link.getDownloadURL(), "https?://[^/]+").getMatch(-1);
            // set formatted host, details has the only page with full filename
            link.setUrlDownload(host + "/details/" + fuid);
        }
    }

    private String getFUID(DownloadLink link) {
        final String fuid = new Regex(link.getDownloadURL(), "https?://(?:www\\.)?gamemaps\\.com/(?:details/|mirrors/|mirrors/mirror/(?:\\d+/)?)(\\d+)").getMatch(0);
        return fuid;
    }

    @Override
    public String getAGBLink() {
        return "http://www.gamemaps.com/terms-of-use";
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return false;
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasAutoCaptcha() {
        return false;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        ddlink = null;
        correctDownloadLink(downloadLink);
        br.setFollowRedirects(true);
        getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("(404 Not Found|This file could not be found on our system)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filenameSize = br.getRegex("<i>\\s*([^>]*\\s*\\([0-9\\,\\.GKMB]+\\))\\s*</i").getMatch(0);
        String filename = new Regex(filenameSize, "(.*?)\\s*\\(").getMatch(0);
        if (filename == null) {
            filename = br.getRegex(">File: <span class=\"content\">(.*?)</span>").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("blocktitle\">\\s*<span>(.*?)</span>").getMatch(0);
                if (filename == null) {
                    // down the bottom you get filename with extension
                    filename = br.getRegex("<span class=\"tag nohover\">(.*?)</span>").getMatch(0);
                }
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filesize = new Regex(filenameSize, "\\((.*?)\\)\\s*$").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex(">Size: <span class=\"content\">(.*?)</span>").getMatch(0);
            if (filesize == null) {
                filesize = br.getRegex("<div class=\"filesize\">(.*?)</div>").getMatch(0);
            }
        }
        downloadLink.setName(filename.trim());
        if (filesize != null) {
            downloadLink.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        requestFileInformation(link);
        checkCacheLink(link);
        if (ddlink == null) {
            final String fuid = getFUID(link);
            // details : background info
            // mirrors : first download page, choose a mirror
            // mirrors/mirror/\d+ : loading page, dl starts in x, if not dling click here...
            // mirrors/mirror/\d+/\d+ : redirect to dl server
            getPage("/mirrors/" + fuid);
            // prefer region over servers numbers?
            final HashMap<String, String> d = new HashMap<String, String>();
            final String[] filter = br.getRegex("<div class=\"mirror (?:themecolor\")?.*?<div class=\"location\">.*?(</div>\\s*){3}").getColumn(-1);
            if (filter != null) {
                for (final String f : filter) {
                    final String location = new Regex(f, "<div class=\"location\">(.*?)</div>").getMatch(0);
                    final String locLink = new Regex(f, "/mirrors/mirror/\\d+/\\d+").getMatch(-1);
                    if (locLink != null && location != null) {
                        d.put(location, locLink);
                    }
                }
            }
            if (!d.isEmpty()) {
                // preference link first,
                final String prefs = prefsLocation();
                if (prefs != null) {
                    for (String k : d.keySet()) {
                        if (k.contains(prefs)) {
                            ddlink = d.get(k);
                            break;
                        }
                    }
                }
                // when none of the locations match user preference
                if (ddlink == null) {
                    // lets pull the first value, hashmaps are stored randomly..
                    ddlink = d.values().iterator().next();
                }
            }
            if (ddlink == null) {
                logger.warning("Could not find location downloadlink");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            getPage(ddlink);
            // no longer meta refresh, but form with delay over javascript > form submit > downloading....(javascript)
            // final Form f = br.getFormbyKey("ad_verif");
            // submitForm(f);
            ddlink = br.getRegex("<meta http-equiv=\"refresh\"[^\r\n]+url=([^\"]+)").getMatch(0);
            if (ddlink == null) {
                // link will be in the format of /mirrors/download/uid/mirrorid > redirects via standard 302 location header
                ddlink = br.getRegex("/mirrors/download/" + fuid + "/\\d+").getMatch(0);
            }
            if (ddlink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, ddlink, true, -10);
        if (!looksLikeDownloadableContent(dl.getConnection())) {
            try {
                br.followConnection(true);
            } catch (IOException e) {
                logger.log(e);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            link.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())));
            link.setProperty(cacheLink, br.getURL());
            dl.startDownload();
        }
    }

    private String prefsLocation() {
        switch (getPluginConfig().getIntegerProperty("servers", -1)) {
        case 0:
            return "USA";
        case 1:
            return "LONDON";
        default:
            return null;
        }
    }

    private String checkCacheLink(final DownloadLink downloadLink) {
        ddlink = downloadLink.getStringProperty(cacheLink);
        if (ddlink != null) {
            URLConnectionAdapter con = null;
            try {
                Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openGetConnection(ddlink);
                if (!looksLikeDownloadableContent(dl.getConnection())) {
                    throw new IOException();
                } else {
                    return ddlink;
                }
            } catch (Exception e) {
                logger.log(e);
                downloadLink.setProperty(cacheLink, Property.NULL);
                ddlink = null;
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        return ddlink;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), "servers", servers, JDL.L("plugins.host.GamMapCm.servers", "Prefer downloads from this location:")).setDefaultValue(0));
    }
}
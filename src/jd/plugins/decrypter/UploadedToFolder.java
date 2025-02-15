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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.net.URLHelper;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "uploaded.to" }, urls = { "https?://(www\\.)?(uploaded\\.(to|net)|ul\\.to)/(f|folder)/[a-z0-9]+" })
public class UploadedToFolder extends PluginForDecrypt {
    public UploadedToFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replaceAll("(www\\.)?(ul\\.to|uploaded\\to)/(f|folder)/", "uploaded.net/f/");
        br.setFollowRedirects(true);
        final String fid = new Regex(parameter, "([a-z0-9]+)$").getMatch(0);
        String baseURL = "http://uploaded.net/";
        if (parameter.contains("https://")) {
            baseURL = baseURL.replace("http://", "https://");
        }
        br.setCookie(baseURL, "lang", "de");
        br.getPage(parameter);
        if (br.containsHTML("(title=\"enthaltene Dateien\" style=\"cursor:help\">\\(0\\)</span>|<i>enthält keine Dateien</i>)")) {
            logger.info("Folder empty: " + parameter);
            return decryptedLinks;
        }
        if (br.getURL().contains(baseURL + "404") || br.containsHTML("(<h1>Seite nicht gefunden<br|>Error: 404<|<title>uploaded.*?\\- where your files have to be uploaded to</title>)")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        String fpName = br.getRegex("<h1><a href=\"folder/[a-z0-9]+\">(.*?)</a></h1>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<title>(.*?)</title>").getMatch(0);
        }
        br.getPage("/list/" + fid + "/plain");
        String[] links = br.getRegex("\"(/?file/[a-z0-9]+)/from/").getColumn(0);
        if (links == null || links.length == 0) {
            links = br.getRegex("(/file/[a-z0-9]+)").getColumn(0);
        }
        String[] folders = br.getRegex("\"(/?folder/[a-z0-9]+)\"").getColumn(0);
        if (folders == null || folders.length == 0) {
            folders = br.getRegex("(/folder/[a-z0-9]+)").getColumn(0);
        }
        if ((links == null || links.length == 0) && (folders == null || folders.length == 0)) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        final URL url = new URL(baseURL);
        if (links != null && links.length != 0) {
            for (final String dl : links) {
                final String dlURL = URLHelper.parseLocation(url, dl);
                final String contentURL = dlURL + "/from/" + fid;
                final DownloadLink downloadLink = createDownloadlink(dlURL);
                downloadLink.setContentUrl(contentURL);
                decryptedLinks.add(downloadLink);
            }
        }
        if (folders != null && folders.length != 0) {
            for (final String dl : folders) {
                if (!dl.contains(fid)) {
                    decryptedLinks.add(createDownloadlink(URLHelper.parseLocation(url, dl)));
                }
            }
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlOnlyDecode(fpName).trim());
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "szort.pl" }, urls = { "https?://(?:www\\.)?szort\\.pl/(?!style\\.css).{3,}" })
public class SzrtPl extends PluginForDecrypt {
    public SzrtPl(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setCustomCharset("iso-8859-2");
        // TODO: Seiten mit Passwort, Seite momentan buggy ...
        if (parameter.contains(".php")) {
            return ret;
        }
        if (parameter.endsWith(".gif")) {
            logger.info("Invalid link: " + parameter);
            return ret;
        }
        String link;
        while (true) {
            br.getPage(parameter);
            if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(?)>\\s*BŁĄD 404 \\- brak strony")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            link = br.getRegex(Pattern.compile("<frame name=\"strona\" src=\"(.*?)\">")).getMatch(0);
            if (link == null) {
                link = br.getRegex("window\\.location\\.href=\"(http[^<>\"]*?)\"").getMatch(0);
            }
            if (link == null) {
                link = br.getRegex("<frame name=\"strona\" src=\"([^<>\"]*?)\">").getMatch(0);
            }
            if (link == null) {
                parameter = br.getRedirectLocation();
                if (parameter == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else {
                    continue;
                }
            } else {
                break;
            }
        }
        ret.add(createDownloadlink(link));
        return ret;
    }

    @Override
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
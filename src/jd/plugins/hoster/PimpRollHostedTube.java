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
package jd.plugins.hoster;

import java.io.IOException;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

/**
 * hostedtube template by pimproll. These guys have various scripts. This is the most common.
 *
 * @see http://www.hostedtube.com/
 * @see http://www.pimproll.com/support.html
 * @linked to wankz.com which still shows "Protranstech BV. DBA NetPass, Postbus 218. ljmudien, 1970AE, Netherlands" in footer message.
 *
 * @author raztoki
 *
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
/* 2020-08-27: Working example: dirtymovie.com */
public class PimpRollHostedTube extends PluginForHost {
    public static String[] t = { "2chicks.com", "3dvideospornos.com", "69hornytube.com", "69xxx.net", "70porn.com", "91porn.us", "adult-lock.com", "adultanimemovies.com", "adultchoice.com", "adultepass.com", "adultfilmconnection.com", "adultfilmflux.com", "adultxxx.xxx", "africanfucktour.org", "africansexgirlfriend.com", "agirls.com", "allasiantgp.com", "allhardcoretgp.com", "amatuerteentube.com", "amazingpornsearch.com", "americanporntube.net", "angkorporn.com", "apetube.eu", "asian-porn.xxx", "asianbdsmtube.com", "asiantranny.xxx", "assfreeass.com", "babes.xxx", "bangsisters.com", "bedroomgraphics.com", "beeg.be", "beegltd.com", "belasnegras.com", "bestporno.net", "bestporntube.com", "bigfullmovies.com", "bindysbabes.com", "bisexualpornhdtube.com", "bisexualpov.com", "bisexualstory.com", "bisexualvideotube.com", "bizarrevidz.com", "bizzarevideotube.com", "blackhoesporn.com",
            "blackpussyporn.net", "blacksextube.net", "blacktube.xxx", "blackwhores.xxx", "bondagefortress.com", "bondagevideos.xxx", "boobworx.com", "bootyspank.com", "bpbr.net", "brasileiro.xxx", "brazzerscasting.com", "bubblebuttboy.com", "bubblebuttboys.com", "buddies.xxx", "buddy.xxx", "bukakebath.com", "bukkakecumshots.com", "buttbashers.com", "cambodialover.com", "cambodiax.com", "capitalpork.com", "cartoonpornpass.com", "cartoonsmaster.com", "celebpornpass.com", "cfnmthings.com", "cloudhentai.com", "clubsixtynine.com", "cockhut.com", "cuckoldtubevids.com", "daddylongtime.com", "dailyhardcorestories.com", "descargarpelisporno.com", "describe.xxx", "dialastud.com", "dirtymovie.com", "dirtyweeman.com", "djwhores.com", "dominatrixescorts.com", "download-free-live-sex-cams.com", "download-free-sex-chat.com", "drfetish.org", "drtubermovies.com", "dumpporn.com", "ebonytubehd.com",
            "enemapov.com", "errvids.com", "exclusivelesbians.com", "explicit.com", "extremedr.com", "fapple.com", "farkingridiculous.com", "farmerotica.com", "feetpornvideos.com", "filmepornogratis.us", "firsttimeanal.org", "fistfullofcum.com", "fraspi.com", "freakofporn.com", "free6.mobi", "free6clips.com", "freeboobtube.com", "freecartoonpornhdtube.com", "freefuckvidz.xxx", "freegaypornhdtube.com", "freelesbianpornhdtube.com", "freemobilevidz.com", "freepornfriend.com", "freepornproject.com", "freepornvideos.name", "freepornwhiz.com", "freeprontube.net", "freesex.info", "freesex22.com", "freesexint.com", "freesexonvideo.com", "freesexpeeps.com", "freesextube.xxx", "freesexvidio.us", "freesmutclub.com", "freetubes.xxx", "freetubexxl.com", "freexxx.org", "freexxxx.xxx", "fuck-tube.org", "fuck13.com", "fucks.se", "fukpics.com", "full-porno.com", "funsexteen.com", "funsexworld.com",
            "galaxysex.com", "gayasslickingvideos.com", "gaycollegesex.com", "gayflicks.com", "gayinteracial.com", "gaymoz.com", "gayrotica.com", "gaytubehub.com", "getmywomen.com", "gigatube.com", "girlstube.net", "gnomeporn.com", "gonzoglamour.com", "greatporndownloads.com", "hankyporn.com", "hardcoretwinks.com", "hctube.com", "hd-porntv.net", "hd-sextv.net", "hentai-files.com", "hentaianimehd.com", "hentaionline.com", "hentaiporno.us", "hentaiporntube.org", "hentaisexvideos.pw", "highpoweredpussy.com", "homotube.nl", "hornyslutmoms.com", "hotarabgirl.com", "hotcamgirls.net", "hoteighteen.com", "hothsexd.com", "hotliveaction.com", "hottube.xxx", "hubdr.com", "hugecockpornpass.com", "hugedickpornpass.com", "ifuck.se", "iizzi.com", "impregnation.net", "indian-sex.xxx", "internetvideounravelled.com", "ismytube.biz", "itube.com", "japenxxx.com", "jenavevejolie.com", "jizz-on.com",
            "jizzdr.com", "joporn.com", "juicypussytube.com", "justaveragesluts.com", "khmer111.com", "knepper.nu", "kudtube.com", "ladysnatch.com", "lesbian-sex.xxx", "lesbianpussy.org", "lesbianxxxvideo.com", "lethalmobile.com", "letsbangthebabysitter.com", "likepussytube.com", "littlesexthings.com", "livesexhq.com", "loadedbox.com", "lolylporno.com", "lopussy.com", "loveinjection.com", "lubebucket.com", "malepornxxx.com", "mamacitavideos.com", "mangaporno.us", "maturehot.com", "maximumpussy.com", "maximumsexxx.com", "mediastoragemadeeasy.com", "menandmen.com", "milfboard.com", "milfphoneporn.com", "milfsporn.net", "milftuba.com", "milftube4u.com", "milftubeporn.com", "milfucker.com", "milfvideos.eu", "milfvideotube.com", "misiko.com", "mobile-hentai-tube.com", "mobileanimepass.com", "mobilecartoontube.com", "mobilemilfpass.com", "mofoscasting.com", "mofoshub.com", "momsfuckteens.com",
            "momspimptheirdaughter.com", "movies-portal.com", "moviesex.xxx", "moviesexcams.com", "mphoneporn.com", "mupp.se", "myxxxplay.com", "n-u-d-e.com", "nakedmovienews.com", "nashvilleporn.com", "nastyblog.com", "nastyhub.com", "nastyporntube.net", "nausty.com", "nobotv.com", "nutten.ru", "nxsnacksvip.com", "ogrishxxx.com", "ohsluts.com", "onlypornclips.com", "orientalpornsearch.com", "paris-porn.net", "pcgn.com", "perfectplatinum.com", "persiankittyvidz.com", "pigyporn.com", "pimptrailers.com", "pinksvisual.com", "pipeporno.com", "playwithmyforeskin.com", "pleasureporn.net", "plumppass.com", "porm.com", "porn-18.xxx", "porn-rabbit.com", "porn-tube.com", "porn69tube.com", "pornadult.xxx", "porndada.com", "porndl.com", "pornfeed.com", "porngoldmine.com", "porninfo.com", "pornmegaplex.com", "pornoactual.com", "pornoextra.com", "pornostartryouts.com", "pornotube.xxx",
            "pornotube21.com", "pornpalazzo.com", "pornpeer.com", "pornporky.com", "pornrss.com", "pornsexlinks.com", "pornsitepros.com", "pornstargalore.net", "pornstarpassion.com", "porntoonz.com", "porntube10.com", "pornvideon.com", "pornvideos10.com", "pornvie.com", "porrfilmer.biz", "porrfilmer.mobi", "porrfilmgratis.se", "primalsexvideos.com", "privatepornlinks.com", "pron.co", "pronhubltd.com", "pussyblog.com", "pussyfights.com", "qualitypink.com", "r89.com", "ratedpornpremium.com", "ratedx.eu", "ratedxmoney.com", "realitylist.com", "realitystation.com", "reallywildteens.com", "redgayporntube.com", "redhotdudes.com", "redtube-com.info", "redzonetube.com", "reet.com", "rubguide.com", "rudeboobies.com", "safesextube.com", "sarahpalinsextape.org", "screwmyspouse.com", "screwshack.com", "sergeantstiffy.com", "sessovideo.us", "sex-cake.com", "sex-film.se", "sex-porno-pics.com",
            "sex-videos.se", "sex.biz", "sexadult.xxx", "sexclicks.com", "sexdgbbw.com", "sexernet.com", "sexfilmer.nu", "sexfilmer.se", "sexfilmsxxx.us", "sexfreevideos.xxx", "sexgratisxxx.us", "sexhamster.co", "sexkate.com", "sexlifetube.com", "sexmom.org", "sexmovielibrary.com", "sexmovieporn.com", "sexmovieshd.com", "sexmoviesxxx.com", "sexmum.com", "sexmys.com", "sexostube.com", "sexparty.tv", "sexpc.com", "sexroots.com", "sexstarclub.com", "sextapexxx.com", "sexthings.com", "sextube-6.com", "sextvnews.com", "sexualexploits.com", "sexualurges.net", "sexvidmovs.com", "sexwebs.com", "sexxxx.xxx", "sexyandold.com", "sexyanimevideos.com", "sexyenema.com", "sexyrhino.com", "shafty.com", "shaftytube.com", "sharmota.com", "simplybabes.com", "skullfucktube.com", "slutloads.us", "smalldickbanger.com", "smalldickfuckers.com", "smutboss.com", "smuthouse.com", "smutshelf.com",
            "soyouthinkyoucanscrew.com", "strapon.se", "straponlesbians.us", "stream-online-porn.com", "sugarmanga.com", "super-porno.com", "superxvideos.net", "swedexxx.com", "sweetchocolatepussy.com", "swesex.se", "swingersmag.com", "swingerstube.net", "swingersx.com", "syntheticheroin.com", "tastyblackpussy.com", "teabaggingpov.com", "teamforbidden.com", "teenbdtube.com", "teenporn2u.info", "teenpornocity.com", "teensteam.com", "teenvideos.com", "thefreeadult.com", "thefreeasian.com", "thefreepornreport.com", "thehotbabes.net", "themeatmen.com", "themostgay.com", "thepornlegacy.com", "thesexlotto.com", "thestagparty.com", "throatpokers.com", "thumblordstube.com", "ticklingpov.com", "tnaflix.eu", "toonfucksluts.com", "topfemales.com", "topsecretporn.net", "totalfetish.com", "totallytastelessvideos.com", "traileraddicts.com", "tramplepov.com", "tube4jizz.com", "tube75.com",
            "tubegalorexxx.com", "tubewetlook.com", "ultimatestripoff.com", "ultramoviemadness.com", "undercoverpussy.com", "usagirlfriends.com", "video18.com", "videofetish.com", "videolivesex.com", "videosp.com", "videoxxxltd.com", "vidsvidsvids.com", "vidxpose.com", "vidz.info", "vidzmobile.com", "vietnamsextube.com", "vip-babes-world.com", "vodporn.eu", "vudutube.com", "wankbucket.com", "watchbooty.com", "watchpornvideos.com", "watchsex.net", "wetandsticky.com", "whoores.com", "whorebrowser.com", "wltube.com", "worldsbestpornmovies.com", "x-movies.xxx", "x-video.xxx", "xblacktube.com", "xcon3.com", "xfree.xxx", "xmovielove.com", "xnxxltd.com", "xnxxx.eu", "xnxxxltd.com", "xpoko.com", "xpornografia.us", "xpornohd.com", "xporntube.us", "xsex.xxx", "xtube.mobi", "xxnxltd.com", "xxx-18.xxx", "xxx-con.com", "xxxbfs.com", "xxxcartoonz.com", "xxxcebu.com", "xxxcreampie.org", "xxxdn.com",
            "xxxfilms.xxx", "xxxgaytube.com", "xxxhardcore.us", "xxxhardcorexxx.com", "xxxhelp.com", "xxxin3d.com", "xxxlesbiansexvideos.org", "xxxlinkshunter.com", "xxxmobiletubes.com", "xxxpornvideos.us", "xxxpornx.xxx", "xxxpromos.com", "xxxslutty.com", "xxxthailand.net", "xxxvidz.org", "xxxxn.com", "youjizz.net", "youjizz66.com", "youjizzltd.com", "youlorn.com", "youpronltd.com", "yoursmutpass.com", "yozjizz.com", "zadmo.com", "zippyporn.com", "contentadult.com", "wankz.com" };

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }

    /**
     * Returns the annotations names array
     *
     * @return
     */
    public static String[] getAnnotationNames() {
        return t;
    }

    /**
     * returns the annotation pattern array
     *
     * @return
     */
    public static String[] getAnnotationUrls() {
        String[] s = new String[t.length];
        for (int ssize = 0; ssize != t.length; ssize++) {
            s[ssize] = constructUrl(t[ssize]);
        }
        /* Special RegExes go here */
        s[s.length - 2] = "https?://(?:www\\.)?contentadult\\.com/contentadult/\\d+";
        s[s.length - 1] = "https?://(?:www\\.)?wankz\\.com/(?:[\\w\\-]+-\\d+|embed/)\\d+";
        return s;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.PimpRoll_HostedTube;
    }

    public static String constructUrl(final String host) {
        /**
         * 2018-04-11: e.g. not accept http://www.adult-lock.com/aactors/12345 </br>
         * 2022-02-25: Now accept such URLs too as old RegEx blocked some legit video URLs too. </br>
         * This will now handle such URLs and display them as offline if they do not lead to video content.
         */
        return "http://(?:(?:www|m)\\.)?" + Pattern.quote(host) + "/[\\w\\-]+/\\d+";
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        if (link.getDownloadURL().contains("wankz.com/")) {
            link.setUrlDownload(link.getDownloadURL().replace("/embed/", "/"));
        }
    }

    public PimpRollHostedTube(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String dllink = null;

    @Override
    public String getAGBLink() {
        return "https://www.pimproll.com/";
    }

    /**
     * defines custom browser requirements.
     */
    private Browser prepBrowser(final Browser prepBr) {
        prepBr.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        prepBr.setFollowRedirects(true);
        return prepBr;
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "(\\d+)$").getMatch(0);
    }

    @Override
    public String getMirrorID(DownloadLink link) {
        String fid = null;
        if (link != null && StringUtils.equals(getHost(), link.getHost()) && (fid = getFID(link)) != null) {
            return getHost() + "://" + fid;
        } else {
            return super.getMirrorID(link);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        prepBrowser(br);
        if (!link.isNameSet()) {
            link.setName(this.getFID(link) + ".mp4");
        }
        br.getPage(link.getPluginPatternMatcher());
        if (br.containsHTML("(?i)was not found on this server, please try a")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.getHttpConnection().getResponseCode() == 410) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // 404 on mobile page
        if (br.containsHTML("<a href=\"#sorting\">")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>([^<>]+)</title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<h1[^>]*>([^<>]*?)</h1>").getMatch(0);
        }
        // DLLINK on desktop page
        final String[] qualities = { "1080p", "720p", "480p", "360p", "240p" };
        for (final String quality : qualities) {
            dllink = br.getRegex("\"" + quality + "\":\"(http[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                /* In most cases e.g.: "name":"480p" */
                dllink = br.getRegex("\"[a-z]+\":\"" + quality + "\",\"url\":\"(http[^<>\"]*?)\"").getMatch(0);
            }
            if (dllink != null) {
                break;
            }
        }
        // DLLINK on mobile page
        if (dllink == null) {
            dllink = br.getRegex("\"(http://[a-z0-9\\-\\.]+(movies|media)\\.hostedtube\\.com/[^<>\"]*?)\"").getMatch(0);
        }
        if (dllink == null) {
            String embed = br.getRegex("(http[^<>]+?/embed/[^<>]+?)&quot;").getMatch(0);
            if (embed != null) {
                // Browser bre = br.cloneBrowser();
                // bre.getPage(embed);
                // To do: find the final link
            }
        }
        if (dllink == null) {
            /* E.g. adult-lock.com */
            final String contentID = br.getRegex("data-item=\"(\\d+)\"").getMatch(0);
            if (contentID == null) {
                /* No video content available */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (filename != null) {
            filename = Encoding.htmlDecode(filename).trim();
            filename = this.correctOrApplyFileNameExtension(filename, ".mp4");
            link.setFinalFileName(filename);
        }
        if (dllink != null) {
            dllink = HTMLEntities.unhtmlentities(dllink);
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            // br2.getHeaders().put("Referer", "");
            if (link.getDownloadURL().contains("xnxxx.eu")) {
                // br2.getHeaders().put("Referer", dllink);
            }
            URLConnectionAdapter con = null;
            try {
                con = br2.openHeadConnection(dllink);
                if (this.looksLikeDownloadableContent(con)) {
                    link.setVerifiedFileSize(con.getCompleteContentLength());
                } else {
                    if (con.getResponseCode() == 401) {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Error 401 unauthorized");
                    } else {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        if (dllink == null && br.containsHTML("(?i)<h3>Access Denied</h3>")) {
            /* E.g. wankz.com */
            throw new AccountRequiredException();
        } else if (dllink == null && br.containsHTML("(?i)>\\s*Free Video Limit Reached")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Free Video Limit Reached", 3 * 60 * 60 * 1000l);
        } else if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, true, 0);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            try {
                br.followConnection(true);
            } catch (final IOException e) {
                logger.log(e);
            }
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server issue");
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}

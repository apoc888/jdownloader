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
package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.DebugMode;
import org.appwork.utils.IO;
import org.appwork.utils.StringUtils;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.parser.UrlQuery;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.jdownloader.controlling.ffmpeg.json.StreamInfo;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.downloader.hls.M3U8Playlist;
import org.jdownloader.plugins.components.config.RedditConfig;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.linkcrawler.LinkCrawlerDeepInspector;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class RedditCom extends PluginForHost {
    public RedditCom(PluginWrapper wrapper) {
        super(wrapper);
        if (DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
            this.enablePremium("https://www.reddit.com/register/");
        }
    }

    public static final String PROPERTY_SUBREDDIT                   = "subreddit";
    public static final String PROPERTY_TITLE                       = "title";
    public static final String PROPERTY_USERNAME                    = "username";
    public static final String PROPERTY_DATE                        = "date";
    public static final String PROPERTY_DATE_TIMESTAMP              = "date_timestamp";
    public static final String PROPERTY_DATE_TIMEDELTA_FORMATTED    = "date_timedelta_formatted";
    public static final String PROPERTY_INDEX                       = "index";
    public static final String PROPERTY_INDEX_MAX                   = "index_max";
    public static final String PROPERTY_SLUG                        = "slug";
    public static final String PROPERTY_POST_ID                     = "postid";
    public static final String PROPERTY_POST_TEXT                   = "post_text";
    public static final String PROPERTY_CRAWLER_FILENAME            = "crawler_filename";
    public static final String PROPERTY_SERVER_FILENAME_WITHOUT_EXT = "server_filename_without_ext";
    public static final String PROPERTY_TYPE                        = "type";
    public static final String PROPERTY_TYPE_text                   = "text";
    public static final String PROPERTY_TYPE_image                  = "image";
    public static final String PROPERTY_TYPE_video                  = "video";

    /** API wiki/docs: https://github.com/reddit-archive/reddit/wiki/API */
    public static final String getApiBaseLogin() {
        return "https://www.reddit.com/api/v1";
    }

    public static final String getApiBaseOauth() {
        return "https://oauth.reddit.com";
    }

    public static final String getClientID() {
        return "TODO_UNDER_DEVELOPMENT";
    }

    public static final String getRedirectURI() {
        return "https://jdownloader.org/";
    }

    public static Browser prepBRAPI(final Browser br) {
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public String getAGBLink() {
        return "https://www.redditinc.com/policies/content-policy";
    }

    private static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "reddit.com" });
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
        final List<String> ret = new ArrayList<String>();
        ret.add("https?://v\\.redd\\.it/[a-z0-9]+|https?://i\\.redd\\.it/[a-z0-9]+\\.[A-Za-z]{2,5}|reddidtext://[a-z0-9]+");
        return ret.toArray(new String[0]);
    }

    /* Connection stuff */
    private final boolean RESUME       = true;
    private final int     MAXCHUNKS    = 0;
    private final int     MAXDOWNLOADS = 20;

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = getFID(link);
        if (linkid != null) {
            return this.getHost() + "://" + linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        final String pluginMatcher = link.getPluginPatternMatcher();
        if (pluginMatcher == null) {
            return null;
        } else if (pluginMatcher.matches(PATTERN_IMAGE)) {
            return new Regex(pluginMatcher, PATTERN_IMAGE).getMatch(0);
        } else if (pluginMatcher.matches(PATTERN_VIDEO)) {
            return new Regex(pluginMatcher, PATTERN_VIDEO).getMatch(0);
        } else if (pluginMatcher.matches(PATTERN_TEXT)) {
            return new Regex(pluginMatcher, PATTERN_TEXT).getMatch(0);
        } else {
            /* Unsupported pattern -> This should never happen! */
            return null;
        }
    }

    @Override
    public String getPluginContentURL(DownloadLink link) {
        if (link != null) {
            final String pluginMatcher = link.getPluginPatternMatcher();
            if (pluginMatcher != null && pluginMatcher.matches(PATTERN_VIDEO)) {
                if (PluginJsonConfig.get(RedditConfig.class).isVideoUseDirecturlAsContentURL()) {
                    /* Return used desired different contentURL. */
                    return getVideoHLSUrls(this.getFID(link));
                }
            }
        }
        /* Return pre-set/default contentURL. */
        return super.getPluginContentURL(link);
    }

    private final String PATTERN_IMAGE = "https?://i\\.redd\\.it/([a-z0-9]+)\\.[A-Za-z]{2,5}";
    private final String PATTERN_VIDEO = "https?://v\\.redd\\.it/([a-z0-9]+)";
    private final String PATTERN_TEXT  = "reddidtext://([a-z0-9]+)";

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        this.setBrowserExclusive();
        br.setAllowedResponseCodes(new int[] { 400 });
        if (link.hasProperty(PROPERTY_CRAWLER_FILENAME)) {
            link.setFinalFileName(link.getStringProperty(PROPERTY_CRAWLER_FILENAME));
        }
        if (link.getPluginPatternMatcher().matches(PATTERN_TEXT)) {
            if (!link.hasProperty(PROPERTY_POST_TEXT)) {
                /* This should never happen */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } else if (link.getPluginPatternMatcher().contains("v.redd.it")) {
            /* HLS Video */
            if (!link.isNameSet()) {
                /* Fallback: Use this if no name was set in crawler. */
                link.setFinalFileName(this.getFID(link) + ".mp4");
            }
            br.getPage(getVideoHLSUrls(this.getFID(link)));
            if (this.br.getHttpConnection().getResponseCode() == 400 || this.br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!LinkCrawlerDeepInspector.looksLikeMpegURL(br.getHttpConnection())) {
                /* Obligatory seconds check. */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!isDownload) {
                final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
                final List<M3U8Playlist> list = M3U8Playlist.loadM3U8(hlsbest.getDownloadurl(), br);
                final HLSDownloader downloader = new HLSDownloader(link, br, br.getURL(), list);
                final StreamInfo streamInfo = downloader.getProbe();
                if (streamInfo == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else {
                    final long estimatedFilesize = downloader.getEstimatedSize();
                    if (estimatedFilesize > 0) {
                        link.setDownloadSize(estimatedFilesize);
                    }
                }
            }
        } else {
            /* Image */
            if (!link.isNameSet()) {
                /* Fallback: Use this if no name was set in crawler. */
                link.setFinalFileName(this.getFID(link) + ".jpg");
            }
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(link.getPluginPatternMatcher());
                /* 2020-07-24: On offline, they will return a dummy image along with responsecode 404. */
                if (con.getResponseCode() == 400 || con.getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                if (con.getContentType().contains("image")) {
                    if (con.getCompleteContentLength() > 0) {
                        link.setVerifiedFileSize(con.getCompleteContentLength());
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    public static final String getVideoHLSUrls(final String videoID) {
        return "https://v.redd.it/" + videoID + "/HLSPlaylist.m3u8";
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link, true);
        handleDownload(link, RESUME, MAXCHUNKS, "free_directlink");
    }

    private void handleDownload(final DownloadLink link, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        if (link.getPluginPatternMatcher().matches(PATTERN_TEXT)) {
            /* Write text to file */
            final File dest = new File(link.getFileOutput());
            IO.writeToFile(dest, link.getStringProperty(PROPERTY_POST_TEXT).getBytes("UTF-8"), IO.SYNC.META_AND_DATA);
            /* Set filesize so user can see it in UI. */
            link.setVerifiedFileSize(dest.length());
            /* Set progress to finished - the "download" is complete ;) */
            link.getLinkStatus().setStatus(LinkStatus.FINISHED);
        } else if (link.getPluginPatternMatcher().contains("v.redd.it")) {
            /* HLS video */
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
            if (hlsbest == null) {
                /* No content available --> Probably the user wants to download hasn't aired yet --> Wait and retry later! */
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Sendung wurde noch nicht ausgestrahlt", 60 * 60 * 1000l);
            }
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, hlsbest.getDownloadurl());
            dl.startDownload();
        } else {
            /* Image */
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getPluginPatternMatcher(), false, 1);
            if (!dl.getConnection().getContentType().contains("image")) {
                logger.warning("The final dllink seems not to be a file!");
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error");
            }
            dl.startDownload();
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return MAXDOWNLOADS;
    }

    private static final String PROPERTY_ACCOUNT_initial_password          = "initial_password";
    private static final String PROPERTY_ACCOUNT_access_token              = "access_token";
    private static final String PROPERTY_ACCOUNT_refresh_token             = "refresh_token";
    private static final String PROPERTY_ACCOUNT_valid_until               = "token_valid_until";
    private static final String PROPERTY_ACCOUNT_token_first_use_timestamp = "token_first_use_timestamp";

    /** Checks to see if e.g. user has changed password. */
    private boolean isSamePW(final Account account) {
        final String initialPW = account.getStringProperty(PROPERTY_ACCOUNT_initial_password);
        return StringUtils.equalsIgnoreCase(initialPW, account.getPass());
    }

    private boolean isAuthorizationURL(final String str) {
        try {
            final UrlQuery query = UrlQuery.parse(str);
            final String state = query.get("state");
            final String code = query.get("code");
            if (StringUtils.isAllNotEmpty(state, code)) {
                return true;
            }
        } catch (final Throwable e) {
            /* No logging needed */
            // logger.log(e);
        }
        return false;
    }

    public void login(final Account account, final boolean validateToken) throws Exception {
        synchronized (account) {
            if (!DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
                showUnderDevelopment();
                throw new AccountInvalidException("The login process of this plugin is still under development");
            }
            br.setCookiesExclusive(true);
            prepBRAPI(br);
            if (!isAuthorizationURL(account.getPass())) {
                /* User did not enter valid login information which can be used for oauth login! */
                /* Reset this property to e.g. try again right away with new token once set by user e.g. if user changes 'password'. */
                account.setProperty(PROPERTY_ACCOUNT_access_token, Property.NULL);
                final String error = UrlQuery.parse(account.getPass()).get("error");
                if (error != null) {
                    /* User has tried authorization but for some reason it failed. */
                    throw new AccountInvalidException("OAuth login failed: " + error);
                }
                /*
                 * User probably entered normal username & password but we need something else as password --> Display dialog with
                 * instructions.
                 */
                showLoginInformation();
                /* Display error to tell user to try again and this time, enter URL into PW field. */
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new AccountInvalidException("Versuch's nochmal und gib die Autorisierungs-URL in das Passwort Feld ein.\r\nGib NICHT dein Passwort ins Passwort Feld ein!");
                } else {
                    throw new AccountInvalidException("Try again and enter your authorization URL in the password field.\r\nDo NOT enter your password into the password field!");
                }
            }
            /*
             * Now set active values - prefer stored values over user-entered as they will change! User-Entered will be used on first login
             * OR if user changes account password!
             */
            String active_refresh_token = null;
            String active_access_token = null;
            String active_valid_until = null;
            long token_first_use_timestamp = 0;
            boolean loggedIN = false;
            if (this.isSamePW(account)) {
                /* Account has been checked before --> Login with stored token */
                logger.info("Trying to re-use login token");
                active_refresh_token = account.getStringProperty(PROPERTY_ACCOUNT_refresh_token, null);
                active_access_token = account.getStringProperty(PROPERTY_ACCOUNT_access_token, null);
                active_valid_until = account.getStringProperty(PROPERTY_ACCOUNT_valid_until);
                token_first_use_timestamp = account.getLongProperty(PROPERTY_ACCOUNT_token_first_use_timestamp, System.currentTimeMillis());
                if (!StringUtils.isEmpty(active_access_token)) {
                    br.getHeaders().put("Authorization", "bearer " + active_access_token);
                    if (!validateToken && System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 5 * 60 * 1000l) {
                        logger.info("Trust token without check");
                        return;
                    }
                    /* TODO: Check which error API will return on expired token. */
                    br.getPage(getApiBaseOauth() + "/api/v1/me/friends");
                    checkErrors(br, null, account);
                    loggedIN = br.getHttpConnection().isOK();
                    if (loggedIN) {
                        /*
                         * Check existing access_token: Perform an API request to check if our access_token is still valid. This will also
                         * ensure that the user has entered his correct username!
                         */
                        br.getPage(getApiBaseOauth() + "/user/" + Encoding.urlEncode(account.getUser()) + "/saved?limit=1");
                        checkErrors(br, null, account);
                        if (!br.getHttpConnection().isOK()) {
                            errorUsernameMismtach();
                        }
                    }
                }
                if (!loggedIN) {
                    /* Build new query containing only what we need. */
                    if (StringUtils.isEmpty(active_refresh_token)) {
                        logger.info("active_refresh_token is not given --> Cannot Refresh login-token --> Login invalid");
                        throw new AccountInvalidException();
                    }
                    logger.info("Trying to generate new authorization token");
                    final UrlQuery loginquery = new UrlQuery();
                    loginquery.add("grant_type", "refresh_token");
                    loginquery.add("refresh_token", Encoding.urlEncode(active_refresh_token));
                    br.getHeaders().put("Authorization", "Basic " + Encoding.Base64Encode(getClientID() + ":"));
                    br.postPage(getApiBaseLogin() + "/access_token", loginquery);
                    active_access_token = PluginJSonUtils.getJson(br, "access_token");
                    active_refresh_token = PluginJSonUtils.getJson(br, "refresh_token");
                    active_valid_until = PluginJSonUtils.getJson(br, "expires_in");
                    if (StringUtils.isEmpty(active_access_token)) {
                        /* Failure e.g. user revoked API access --> Invalid logindata --> Permanently disable account */
                        checkErrors(this.br, null, account);
                        throw new AccountInvalidException();
                    }
                    /* Update authorization header */
                    br.getHeaders().put("Authorization", "Bearer " + active_access_token);
                    /* Update token first use timestamp */
                    token_first_use_timestamp = System.currentTimeMillis();
                }
            } else {
                /* First login */
                final UrlQuery query = UrlQuery.parse(account.getPass());
                final String code = query.get("code");
                logger.info("Performing first / full login");
                final UrlQuery loginquery = new UrlQuery();
                loginquery.add("grant_type", "authorization_code");
                loginquery.add("code", Encoding.urlEncode(code));
                loginquery.add("redirect_uri", Encoding.urlEncode(getRedirectURI()));
                br.getHeaders().put("Authorization", "Basic " + Encoding.Base64Encode(getClientID() + ":"));
                br.postPage(getApiBaseLogin() + "/access_token", loginquery);
                active_access_token = PluginJSonUtils.getJson(br, "access_token");
                active_refresh_token = PluginJSonUtils.getJson(br, "refresh_token");
                active_valid_until = PluginJSonUtils.getJson(br, "expires_in");
                if (StringUtils.isEmpty(active_access_token)) {
                    /* Failure e.g. user revoked API access --> Invalid logindata --> Permanently disable account */
                    checkErrors(this.br, null, account);
                    throw new AccountInvalidException();
                }
            }
            account.setProperty(PROPERTY_ACCOUNT_access_token, active_access_token);
            if (!StringUtils.isEmpty(active_refresh_token)) {
                account.setProperty(PROPERTY_ACCOUNT_refresh_token, active_refresh_token);
            }
            if (active_valid_until != null && active_valid_until.matches("\\d+")) {
                account.setProperty(PROPERTY_ACCOUNT_valid_until, Long.parseLong(active_valid_until));
            }
            account.setProperty(PROPERTY_ACCOUNT_token_first_use_timestamp, token_first_use_timestamp);
            account.setProperty(PROPERTY_ACCOUNT_initial_password, account.getPass());
            /* Save cookies - but only so that we have the cookie-timestamp */
            account.saveCookies(br.getCookies(this.getHost()), "");
        }
    }

    private void errorUsernameMismtach() throws PluginException {
        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
            throw new AccountInvalidException("Falscher Benutzername: Dein Account ist gültig, aber der Benutzername ist nicht der mit dem du in deinem Browser angemeldet bist!");
        } else {
            throw new AccountInvalidException("Username mismatch: Please enter the username in which you are logged in in your browser!");
        }
    }

    public String userlessLogin(final Browser brlogin) throws IOException {
        brlogin.getHeaders().put("Authorization", "Basic " + Encoding.Base64Encode(getClientID() + ":"));
        final UrlQuery loginquery = new UrlQuery();
        loginquery.add("grant_type", "client_credentials");
        loginquery.add("device_id", "12345678912345678912");
        brlogin.postPage(getApiBaseLogin() + "/access_token", loginquery);
        return PluginJSonUtils.getJson(br, "access_token");
    }

    private void checkErrors(final Browser br, final DownloadLink link, final Account account) throws PluginException {
        if (br.getHttpConnection().getResponseCode() == 429) {
            rateLimitReached(br, account);
        }
        final Map<String, Object> entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
        final String errormsg = (String) entries.get("message");
        final long errorcode = JavaScriptEngineFactory.toLong(entries.get("error"), 0);
        if (errorcode == 403) {
            /* TODO */
        }
    }

    /* TODO: Check if this works */
    private void rateLimitReached(final Browser br, final Account account) throws PluginException {
        long reset_in = 0;
        /* This header will usually tell us once rate limit is over (at least when an account was used) */
        final String api_reset_in = br.getRequest().getResponseHeader("X-RateLimit-UserReset");
        if (api_reset_in != null && api_reset_in.matches("\\d+")) {
            reset_in = Long.parseLong(api_reset_in);
        }
        final long waittime;
        if (reset_in > System.currentTimeMillis()) {
            waittime = reset_in - System.currentTimeMillis() + 10000l;
        } else {
            /* Default waittime */
            waittime = 5 * 60 * 1000l;
        }
        if (account != null) {
            throw new AccountUnavailableException("API Rate Limit reached", waittime);
        } else {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "API Rate Limit reached", waittime);
        }
    }

    private Thread showLoginInformation() {
        final String authURL = getApiBaseLogin() + "/authorize?client_id=" + getClientID() + "&response_type=code&state=TODO&redirect_uri=" + Encoding.urlEncode(getRedirectURI()) + "&duration=permanent&scope=read%20history";
        final Thread thread = new Thread() {
            public void run() {
                try {
                    String message = "";
                    final String title;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        title = "Reddit.com - Login";
                        message += "Hallo liebe(r) Reddit NutzerIn\r\n";
                        message += "Um deinen Reddit Account in JD verwenden zu können, musst du folgende Schritte beachten:\r\n";
                        message += "1. Öffne diesen Link im Browser falls das nicht automatisch passiert:\r\n\t'" + authURL + "'\t\r\n";
                        message += "2. Autorisiere JD auf der Reddit Webseite.\r\nDu wirst weitergeleitet auf 'jdownloader.org/?state=...'.\r\nKopiere diesen Link aus der Adresszeile und gib ihn ins 'Passwort' Feld der Reddit Loginmaske in JD ein.\r\n";
                        message += "Dein Account sollte nach einigen Sekunden von JDownloader akzeptiert werden.\r\n";
                    } else {
                        title = "Reddit.com - Login";
                        message += "Hello dear Reddit user\r\n";
                        message += "In order to use Reddit with JD, you need to follow these steps:\r\n";
                        message += "1. Open the following URL in your browser if it is not opened automatically:\r\n\t'" + authURL + "'\t\r\n";
                        message += "2. Authorize JD on the Reddit website.\r\nYou will be redirected to 'jdownloader.org/?state=...'.\r\nCopy this complete URL from the address bar of your browser and enter it into the password field of the Reddit login mask in JD. \r\n";
                        message += "Your account should be accepted in JDownloader within a few seconds.\r\n";
                    }
                    final ConfirmDialog dialog = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, title, message);
                    dialog.setTimeout(2 * 60 * 1000);
                    if (CrossSystem.isOpenBrowserSupported() && !Application.isHeadless()) {
                        CrossSystem.openURL(authURL);
                    }
                    final ConfirmDialogInterface ret = UIOManager.I().show(ConfirmDialogInterface.class, dialog);
                    ret.throwCloseExceptions();
                } catch (final Throwable e) {
                    getLogger().log(e);
                }
            };
        };
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private Thread showUnderDevelopment() {
        final String forumURL = "https://board.jdownloader.org/showthread.php?t=80259";
        final Thread thread = new Thread() {
            public void run() {
                try {
                    String message = "";
                    final String title;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        title = "Reddit.com - In Entwicklung";
                        message += "Hallo liebe(r) Reddit NutzerIn\r\n";
                        message += "Dieses Plugin befindet sich derzeit noch in Entwicklung.\r\n";
                        message += "Siehe: :\r\n\t'" + forumURL + "'\t\r\n";
                    } else {
                        title = "Reddit.com - Under development";
                        message += "Hello dear Reddit user\r\n";
                        message += "This plugin is still under development.\r\n";
                        message += "See:\r\n\t'" + forumURL + "'\t\r\n";
                    }
                    final ConfirmDialog dialog = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, title, message);
                    dialog.setTimeout(2 * 60 * 1000);
                    if (CrossSystem.isOpenBrowserSupported() && !Application.isHeadless()) {
                        CrossSystem.openURL(forumURL);
                    }
                    final ConfirmDialogInterface ret = UIOManager.I().show(ConfirmDialogInterface.class, dialog);
                    ret.throwCloseExceptions();
                } catch (final Throwable e) {
                    getLogger().log(e);
                }
            };
        };
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        /*
         * 2020-07-23: We're trying to request minimal API permissions (via oauth2 scopes) so we don't get access to the users' profile -->
         * Just display all accounts as free accounts! To get information about the users' profile, we'd have to additionally request the
         * scope "identity": https://github.com/reddit-archive/reddit/wiki/OAuth2
         */
        // br.getPage(getApiBaseOauth() + "/api/v1/me");
        ai.setUnlimitedTraffic();
        account.setType(AccountType.FREE);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /* Login is not required for any reddit content! */
        this.handleFree(link);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return MAXDOWNLOADS;
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final Account acc) {
        /* No captchas at all */
        return false;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public Class<? extends RedditConfig> getConfigInterface() {
        return RedditConfig.class;
    }
}
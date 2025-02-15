package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.appwork.storage.JSonMapperException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.RFC2047;
import org.appwork.utils.logging2.LogInterface;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.config.Keep2shareConfig;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.proxy.AbstractProxySelectorImpl;
import jd.controlling.reconnect.ipcheck.BalancedWebIPCheck;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.nutils.Formatter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.AccountRequiredException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.download.DownloadInterface;

/**
 * Abstract class supporting keep2share/fileboom/publish2<br/>
 * <a href="https://github.com/keep2share/api/">Github documentation</a>
 *
 * @author raztoki
 *
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public abstract class K2SApi extends PluginForHost {
    private final String lng                        = getLanguage();
    private final String PROPERTY_ACCOUNT_AUTHTOKEN = "auth_token";
    /* Reconnect workaround settings */
    private final String PROPERTY_FILE_ID           = "fileID";
    private final String PROPERTY_LASTDOWNLOAD      = "_lastdownload_timestamp";
    private final String PROPERTY_ACCESS            = "access";
    private final long   FREE_RECONNECTWAIT_MILLIS  = 1 * 60 * 60 * 1000L;

    public K2SApi(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://" + this.getHost() + "/premium.html");
    }

    @Override
    public String getAGBLink() {
        return "https://" + this.getHost() + "/page/terms.html";
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        /* Respect users protocol choosing. */
        link.setPluginPatternMatcher(link.getPluginPatternMatcher().replaceFirst("^https?://", getProtocol()));
    }

    protected int getMaxChunks(final Account account) {
        if (account == null || account.getType() == AccountType.FREE) {
            /* Free Account and no account */
            return 1;
        } else {
            /* Premium account */
            return -10;
        }
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        /* 2022-09-16: Resume is always allowed */
        return true;
    }

    @Override
    public boolean enoughTrafficFor(final DownloadLink link, Account account) throws Exception {
        final String directlinkproperty = getDirectLinkProperty(account);
        final String dllink = link.getStringProperty(directlinkproperty);
        if (StringUtils.isNotEmpty(dllink)) {
            return true;
        } else {
            return super.enoughTrafficFor(link, account);
        }
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = this.getFUID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getRefererFromURL(final DownloadLink link) {
        String url_referer = null;
        try {
            /* Try-catch to allow other plugins to use other patterns */
            url_referer = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(4);
        } catch (final Throwable e) {
        }
        return url_referer;
    }

    private String getFallbackFilename(final DownloadLink link) {
        String name_url = null;
        try {
            /* Try-catch to allow other plugins to use other patterns */
            name_url = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(2);
        } catch (final Throwable e) {
        }
        if (name_url == null) {
            name_url = this.getFUID(link);
        }
        return name_url;
    }

    /**
     * Sets domain the API will use!
     *
     */
    protected abstract String getInternalAPIDomain();

    /**
     * Does the site enforce HTTPS? <br />
     * Override this when incorrect<br />
     * <b>NOTE:</b> When setting to true, make sure that supportsHTTPS is also set to true!
     *
     * @return
     */
    protected boolean enforcesHTTPS() {
        // 23.08.2021, SSL is enforced
        return true;
    }

    @Override
    public void resetLink(DownloadLink link) {
    }

    protected boolean isValidDownloadConnection(final URLConnectionAdapter con) {
        final String contentType = con.getContentType();
        if (StringUtils.contains(contentType, "text") || StringUtils.containsIgnoreCase(contentType, "html") || con.getCompleteContentLength() == -1 || con.getResponseCode() == 401 || con.getResponseCode() == 404 || con.getResponseCode() == 409 || con.getResponseCode() == 440) {
            return false;
        } else {
            return looksLikeDownloadableContent(con);
        }
    }

    protected String getDirectLinkProperty(final Account account) {
        if (account == null) {
            return "freelink1";
        } else {
            switch (account.getType()) {
            case FREE:
                return "freelink2";
            case PREMIUM:
            default:
                return "premlink";
            }
        }
    }

    protected final String getStoredDirecturl(final DownloadLink link, final Account account) {
        return link.getStringProperty(this.getDirectLinkProperty(account));
    }

    /**
     * returns API Revision number as long
     *
     * @author Jiaz
     */
    protected long getAPIRevision() {
        return Math.max(0, Formatter.getRevision("$Revision$"));
    }

    /**
     * returns String in friendly format, to be used in logger outputs.
     *
     * @author raztoki
     */
    protected String getRevisionInfo() {
        return "RevisionInfo: " + this.getClass().getSimpleName() + "=" + Math.max(getVersion(), 0) + ", K2SApi=" + getAPIRevision();
    }

    @Override
    public long getVersion() {
        return (Math.max(super.getVersion(), 0) * 100000) + getAPIRevision();
    }

    /**
     * Does the site support HTTPS? <br />
     * Override this when incorrect
     *
     * @return
     */
    protected boolean userPrefersHTTPS() {
        // 2021-08-23: SSL is enforced
        return true;
    }

    protected boolean isUseAPIDefaultEnabled() {
        return true;
    }

    /**
     * useAPI frame work? <br />
     * Override this when incorrect
     *
     * @return
     */
    protected boolean useAPI() {
        /* 2020-05-09: Website mode not supported anymore. */
        return true;
    }

    protected String getApiUrl() {
        return getProtocol() + getInternalAPIDomain() + "/api/v2";
    }

    /**
     * Returns plugin specific user setting. <br />
     * <b>NOTE:</b> public method, so that the decrypter can use it!
     *
     * @author raztoki
     * @return
     */
    public String getProtocol() {
        return (isSecure() ? "https://" : "http://");
    }

    /** Returns whether or not https is to be used. */
    protected boolean isSecure() {
        if (enforcesHTTPS()) {
            return true;
        } else if (userPrefersHTTPS()) {
            return true;
        } else {
            return false;
        }
    }

    protected boolean isSpecialFUID(final String fuid) {
        return false;
    }

    protected String getFUID(final DownloadLink link) {
        final String fileID = link.getStringProperty(PROPERTY_FILE_ID);
        if (StringUtils.isNotEmpty(fileID)) {
            return fileID;
        } else {
            return getFUID(link.getPluginPatternMatcher());
        }
    }

    public String getFUID(final String link) {
        return new Regex(link, this.getSupportedLinks()).getMatch(0);
    }

    private static HashMap<String, String> antiDDoSCookies = new HashMap<String, String>();
    // private static AtomicReference<String> agent = new AtomicReference<String>(null);
    private boolean                        prepBrSet       = false;

    @Override
    public void init() {
        try {
            Browser.setBurstRequestIntervalLimitGlobal(getInternalAPIDomain(), true, 3000, 20, 60000);
            Browser.setRequestIntervalLimitGlobal(getInternalAPIDomain(), 2000);
        } catch (final Throwable t) {
            logger.log(t);
        }
        final String[] siteSupportedNames = siteSupportedNames();
        if (siteSupportedNames != null) {
            for (String siteSupportedName : siteSupportedNames) {
                try {
                    Browser.setRequestIntervalLimitGlobal(siteSupportedName, 2000);
                } catch (final Throwable ignore) {
                    logger.log(ignore);
                }
            }
        }
    }

    protected Browser prepBrowser(final Browser br) {
        // define custom browser headers and language settings.
        // required for native cloudflare support, without the need to repeat requests.
        br.addAllowedResponseCodes(new int[] { 429, 503, 520, 522 });
        synchronized (antiDDoSCookies) {
            if (!antiDDoSCookies.isEmpty()) {
                for (final Map.Entry<String, String> cookieEntry : antiDDoSCookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    final String value = cookieEntry.getValue();
                    br.setCookie(this.getHost(), key, value);
                }
            }
        }
        br.getHeaders().put("User-Agent", "JDownloader." + getVersion());
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        br.getHeaders().put("Accept-Charset", null);
        // prepBr.getHeaders().put("Cache-Control", null);
        br.getHeaders().put("Pragma", null);
        br.setConnectTimeout(90 * 1000);
        br.setReadTimeout(90 * 1000);
        prepBrSet = true;
        return br;
    }

    private Browser prepAPI(final Browser br) {
        // prep site variables, this links back to prepADB from Override
        prepBrowser(br);
        // api && dl server response codes
        br.addAllowedResponseCodes(new int[] { 400, 401, 403, 406 });
        return br;
    }

    protected String getLinkIDDomain() {
        return getHost();
    }

    public boolean checkLinks(final DownloadLink[] urls) {
        // required to get overrides to work
        final Browser br = prepAPI(new Browser());
        try {
            final List<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                final ArrayList<String> fileIDs = new ArrayList<String>();
                while (true) {
                    if (links.size() == 100 || index == urls.length) {
                        break;
                    } else {
                        final DownloadLink dl = urls[index];
                        final String fuid = getFUID(dl);
                        links.add(dl);
                        fileIDs.add(fuid);
                        index++;
                    }
                }
                try {
                    try {
                        final HashMap<String, Object> postdata = new HashMap<String, Object>();
                        postdata.put("ids", fileIDs);
                        final Map<String, Object> entries = postPageRaw(br, "/getfilesinfo", postdata, null);
                        final List<Map<String, Object>> files = (List<Map<String, Object>>) entries.get("files");
                        for (final DownloadLink dl : links) {
                            final String fuid = getFUID(dl);
                            Map<String, Object> fileInfo = null;
                            for (final Map<String, Object> fileInfoTmp : files) {
                                /*
                                 * Every link has this id but can have an unlimited number of "requested_id" values pointing to the same
                                 * content.
                                 */
                                final String id = fileInfoTmp.get("id").toString();
                                final String requested_id = (String) fileInfoTmp.get("requested_id");
                                if (id.equals(fuid)) {
                                    fileInfo = fileInfoTmp;
                                    break;
                                } else if (StringUtils.equals(requested_id, fuid)) {
                                    /* For links with two/old/special fileIDs. */
                                    fileInfo = fileInfoTmp;
                                    break;
                                }
                            }
                            if (fileInfo == null) {
                                /* ID was not in result --> Probably ID has invalid format --> It's also definitely offline! */
                                dl.setAvailable(false);
                            } else {
                                final String id = (String) fileInfo.get("id");
                                if (!StringUtils.equals(fuid, id)) {
                                    /* Save internal ID as we use this for dupe-checking. */
                                    dl.setProperty(PROPERTY_FILE_ID, id);
                                }
                                if (((Boolean) fileInfo.get("is_available")) == Boolean.TRUE) {
                                    dl.setAvailable(true);
                                } else {
                                    dl.setAvailable(false);
                                }
                                String name = (String) fileInfo.get("name");
                                if (name != null && name.matches(".*=(\\?|_)utf-8(\\?|_).+")) {
                                    // workaround for rfc2047 support
                                    try {
                                        final CharSequence fixed = new RFC2047().decode(name, true);
                                        if (fixed != null && fixed != name) {
                                            name = fixed.toString();
                                        }
                                    } catch (IOException e) {
                                        logger.log(e);
                                    }
                                }
                                final Object sizeO = fileInfo.get("size");
                                final String md5 = (String) fileInfo.get("md5");// only available for file owner
                                final String access = (String) fileInfo.get("access");
                                if (!StringUtils.isEmpty(name)) {
                                    dl.setFinalFileName(name);
                                } else if (!dl.isNameSet()) {
                                    dl.setName(getFallbackFilename(dl));
                                }
                                if (sizeO instanceof Number) {
                                    dl.setVerifiedFileSize(((Number) sizeO).longValue());
                                }
                                if (!StringUtils.isEmpty(md5)) {
                                    dl.setMD5Hash(md5);
                                }
                                if (!StringUtils.isEmpty(access)) {
                                    // access: ['public', 'private', 'premium']
                                    // public = everyone users
                                    // premium = restricted to premium
                                    // private = owner only..
                                    dl.setProperty(PROPERTY_ACCESS, access);
                                    if (dl.getComment() == null) {
                                        if ("premium".equalsIgnoreCase(access)) {
                                            dl.setComment(getErrorMessageForUser(7));
                                        } else if ("private".equalsIgnoreCase(access)) {
                                            dl.setComment(getErrorMessageForUser(8));
                                        }
                                    }
                                }
                                if ((Boolean) fileInfo.get("is_folder") == Boolean.TRUE) {
                                    /* This should never happen */
                                    dl.setAvailable(false);
                                    if (dl.getComment() == null) {
                                        dl.setComment(getErrorMessageForUser(23));
                                    }
                                }
                            }
                        }
                    } catch (final PluginException e) {
                        if (br.getHttpConnection().getResponseCode() == 400) {
                            final Map<String, Object> root = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
                            if (StringUtils.equals((String) root.get("message"), "Invalid request params")) {
                                /**
                                 * 2022-02-25: Workaround for when checking only one <b>invalid</b> fileID e.g.
                                 * "2ahUKEwiUlaOqlZv2AhWLyIUKHXOjAmgQuZ0HegQIARBG". </br>
                                 * This may also happen when there are multiple fileIDs to check and all of them are invalid.
                                 */
                                for (final DownloadLink dl : links) {
                                    dl.setAvailable(false);
                                }
                                continue;
                            } else {
                                throw e;
                            }
                        }
                        throw e;
                    }
                } finally {
                    if (index == urls.length) {
                        break;
                    }
                }
            }
        } catch (final Exception e) {
            logger.log(e);
            return false;
        }
        return true;
    }

    /*
     * IMPORTANT: Current implementation seems to be correct - admin told us that there are no lifetime accounts (anymore)
     */
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        logger.info(getRevisionInfo());
        final AccountInfo ai = new AccountInfo();
        /* required to get overrides to work */
        prepAPI(br);
        final String auth_token = getAuthToken(account, null, true);
        final Map<String, Object> entries;
        if (!br.getURL().endsWith("/accountinfo")) {
            entries = getAccountInfoViaAPI(account, br, auth_token);
        } else {
            /* Request has already been done before. */
            entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.HASHMAP);
        }
        final Number available_traffic = (Number) entries.get("available_traffic");
        /*
         * 2019-11-26: Expired premium accounts will have their old expire-date given thus we'll have to check for that before setting
         * expire-date or such free accounts cannot be used! For Free Accounts which have never bought any premium package, this will be
         * returned instead: "account_expires":false
         */
        final Object account_expiresO = entries.get("account_expires");
        long account_expires_timestamp = 0;
        if (account_expiresO instanceof Number) {
            account_expires_timestamp = ((Number) account_expiresO).longValue() * 1000;
        }
        if (account_expires_timestamp < System.currentTimeMillis()) {
            /* 2019-11-26: Free Accounts are supposed to get 100 KB/s downloadspeed but at least via API this did not work for me. */
            /*
             * 2019-12-03: Free Account limits are basically the same as via browser. API will return 10GB traffic for free accounts but
             * after 1-2 downloads, users will get a IP_BLOCKED waittime of 60+ minutes. With a new IP, traffic of the free account will
             * reset to 10GB and more downloads are possible. However, often users will have to enter a login-captcha when logging in the
             * same account with a new IP!
             */
            account.setType(AccountType.FREE);
            if (account_expires_timestamp > 0) {
                /* Account was once a premium account */
                ai.setStatus("Free Account (expired premium)");
            } else {
                /* Account has always been a free account - user never bought any premium packages */
                ai.setStatus("Free Account");
            }
            account.setAllowReconnectToResetLimits(true);
        } else {
            account.setType(AccountType.PREMIUM);
            ai.setValidUntil(account_expires_timestamp);
            ai.setStatus("Premium Account");
        }
        if (available_traffic != null) {
            ai.setTrafficLeft(available_traffic.longValue());
        }
        setAccountLimits(account);
        return ai;
    }

    /** See https://keep2share.github.io/api/#resources:/accountInfo:post */
    private Map<String, Object> getAccountInfoViaAPI(final Account account, final Browser br, final String auth_token) throws Exception {
        final HashMap<String, Object> postdata = new HashMap<String, Object>();
        postdata.put("auth_token", auth_token);
        return postPageRaw(br, "/accountinfo", postdata, account, null);
    }

    protected void setAccountLimits(Account account) {
        final int max;
        switch (account.getType()) {
        case PREMIUM:
            max = 20;
            break;
        default:
            max = 1;
            break;
        }
        account.setMaxSimultanDownloads(max);
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        if (checkShowFreeDialog(getHost())) {
            showFreeDialog(getHost());
        }
        handleDownload(link, null);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception, PluginException {
        handleDownload(link, account);
    }

    public void handleDownload(final DownloadLink link, final Account account) throws Exception {
        logger.info(getRevisionInfo());
        /* Check link */
        reqFileInformation(link);
        final String fuid = getFUID(link);
        String dllink = this.getStoredDirecturl(link, account);
        // required to get overrides to work
        prepAPI(br);
        // because opening the link to test it, uses up the availability, then reopening it again = too many requests too quickly issue.
        if (!StringUtils.isEmpty(dllink)) {
            final Browser obr = br.cloneBrowser();
            logger.info("Reusing cached finallink!");
            this.dl = new jd.plugins.BrowserAdapter().openDownload(obr, link, dllink, this.isResumeable(link, account), this.getMaxChunks(account));
            if (!isValidDownloadConnection(dl.getConnection())) {
                logger.warning("The final dllink seems not to be a file!");
                try {
                    obr.followConnection(true);
                } catch (IOException e) {
                    logger.log(e);
                }
                /*
                 * 2019-11-26: Outdated downloadurls will return precise errors (only text) e.g. "This link assigned with other IP address"
                 * or
                 * "Download link is outdated, invalid or assigned to another IP address.If you see this error, most likely you will need to get new download link"
                 */
                handleGeneralServerErrors(obr, dl, account, link);
                // we now want to restore!
                br = obr;
                dllink = null;
                this.dl = null;
            }
        }
        final boolean isFree = this.isNoAccountOrFreeAccount(account);
        /* if above has failed, dllink will be null */
        if (StringUtils.isEmpty(dllink)) {
            logger.info("Trying to generate new directurl");
            if ("premium".equalsIgnoreCase(link.getStringProperty(PROPERTY_ACCESS)) && isFree) {
                // download not possible
                premiumDownloadRestriction(getErrorMessageForUser(3));
            } else if ("private".equalsIgnoreCase(link.getStringProperty(PROPERTY_ACCESS)) && isFree) {
                privateDownloadRestriction(getErrorMessageForUser(8));
            }
            String currentIP = null;
            if (isFree && PluginJsonConfig.get(this.getConfigInterface()).isEnableReconnectWorkaround()) {
                /**
                 * Experimental reconnect handling to prevent having to enter a captcha just to see that a limit has been reached!
                 */
                currentIP = new BalancedWebIPCheck(null).getExternalIP().getIP();
                final String downloadlimitText = "Downloadlimit reached";
                logger.info("New free/free-account Download: currentIP = " + currentIP);
                if (account != null) {
                    final long lastdownloadFreeAccountTimestampMillis = account.getLongProperty(PROPERTY_LASTDOWNLOAD, 0);
                    final long passedTimeMillisSinceLastFreeAccountDownload = System.currentTimeMillis() - lastdownloadFreeAccountTimestampMillis;
                    if (passedTimeMillisSinceLastFreeAccountDownload < FREE_RECONNECTWAIT_MILLIS) {
                        logger.info("Experimental handling active --> There still seems to be a waittime on the current account --> ERROR_IP_BLOCKED to prevent unnecessary captcha");
                        throw new AccountUnavailableException(downloadlimitText, FREE_RECONNECTWAIT_MILLIS - passedTimeMillisSinceLastFreeAccountDownload);
                    }
                }
                final Map<String, Long> blockedIPsMap = this.getBlockedIPsMap();
                synchronized (blockedIPsMap) {
                    /* Load list of saved IPs + timestamp of last download and add it to our main map */
                    try {
                        final Map<String, Long> lastdownloadmap = (Map<String, Long>) this.getPluginConfig().getProperty(PROPERTY_LASTDOWNLOAD);
                        if (lastdownloadmap != null && blockedIPsMap.isEmpty()) {
                            blockedIPsMap.putAll(lastdownloadmap);
                        }
                    } catch (final Exception ignore) {
                    }
                }
                /*
                 * If the user starts a download in free (unregistered) mode the waittime is on his IP. This also affects free accounts if
                 * he tries to start more downloads via free accounts afterwards BUT nontheless the limit is only on his IP so he CAN
                 * download using the same free accounts after performing a reconnect!
                 */
                final long lastdownload = getPluginSavedLastDownloadTimestamp(currentIP);
                final long passedTimeSinceLastDl = System.currentTimeMillis() - lastdownload;
                if (passedTimeSinceLastDl < FREE_RECONNECTWAIT_MILLIS) {
                    logger.info("Experimental handling active --> There still seems to be a waittime on the current IP --> ERROR_IP_BLOCKED to prevent unnecessary captcha");
                    final long thisWait = FREE_RECONNECTWAIT_MILLIS - passedTimeSinceLastDl;
                    if (account != null) {
                        throw new AccountUnavailableException(downloadlimitText, thisWait);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, downloadlimitText, thisWait);
                    }
                }
            }
            final Map<String, Object> getURL = new HashMap<String, Object>();
            getURL.put("file_id", fuid);
            if (isFree) {
                final Map<String, Object> requestcaptcha = postPageRaw(this.br, "/requestcaptcha", new HashMap<String, Object>(), account, link);
                final String challenge = (String) requestcaptcha.get("challenge");
                String captcha_url = (String) requestcaptcha.get("captcha_url");
                // Dependency
                if (StringUtils.isEmpty(challenge) || StringUtils.isEmpty(captcha_url)) {
                    logger.warning("challenge = " + challenge + " | captcha_url = " + captcha_url);
                    this.handleErrorsAPI(account, link, this.br);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                if (!captcha_url.startsWith("https://")) {
                    /*
                     * 2020-02-03: Possible workaround for this issues reported here: board.jdownloader.org/showthread.php?t=82989 and
                     * 2020-04-23: board.jdownloader.org/showthread.php?t=83927 and board.jdownloader.org/showthread.php?t=83781
                     */
                    logger.info("download-captcha_url is not https --> Changing it to https");
                    captcha_url = captcha_url.replace("http://", "https://");
                }
                final String code = getCaptchaCode(captcha_url, link);
                if (StringUtils.isEmpty(code)) {
                    // captcha can't be blank! Why we don't return null I don't know (raztoki?)!
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                getURL.put("captcha_challenge", challenge);
                getURL.put("captcha_response", code);
                final String custom_referer = getCustomReferer(link);
                if (StringUtils.isNotEmpty(custom_referer)) {
                    logger.info("Using Referer value: " + custom_referer);
                    getURL.put("url_referrer", custom_referer);
                } else {
                    logger.info("Using Referer value: NONE given");
                }
            }
            if (account != null) {
                /* Premium + free Account */
                getURL.put("auth_token", getAuthToken(account, link, false));
            }
            Map<String, Object> geturlResponse = postPageRaw(this.br, "/geturl", getURL, account, link);
            final String free_download_key = (String) geturlResponse.get("free_download_key");
            boolean resumable = this.isResumeable(link, account);
            int maxChunks = this.getMaxChunks(account);
            if (!StringUtils.isEmpty(free_download_key)) {
                /* Free and free-account download */
                /*
                 * {"status":"success","code":200,"message":"Captcha accepted, please wait","free_download_key":"homeHash","time_wait":30}
                 */
                final Number time_wait = (Number) geturlResponse.get("time_wait");
                if (time_wait == null || free_download_key == null) {
                    /* This should never happen */
                    this.handleErrorsAPI(account, link, this.br);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                /*
                 * 2020-02-18: Add 2 extra seconds else this might happen after sending captcha answer:
                 * {"status":"success","code":200,"message" :"Captcha accepted, please wait","free_download_key":"CENSORED","time_wait":1}
                 */
                final int wait_seconds = time_wait.intValue();
                final long waitMillis = (wait_seconds + 2) * 1000l;
                if (wait_seconds > 180) {
                    if (waitMillis <= FREE_RECONNECTWAIT_MILLIS) {
                        final long waittimeStartedBeforeMillis = FREE_RECONNECTWAIT_MILLIS - waitMillis;
                        final long timestampWaittimeStarted = System.currentTimeMillis() - waittimeStartedBeforeMillis;
                        this.saveFreeLimit(account, currentIP, timestampWaittimeStarted);
                    }
                    if (account != null) {
                        /*
                         * 2020-05-11: Account traffic will be reset after reconnect too but without reconnect, user will have to wait that
                         * time until he can start new DLs with a free account!
                         */
                        throw new AccountUnavailableException("Downloadlimit reached", waitMillis);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, waitMillis);
                    }
                }
                sleep(waitMillis, link);
                getURL.put("free_download_key", free_download_key);
                getURL.remove("captcha_challenge");
                getURL.remove("captcha_response");
                geturlResponse = postPageRaw(this.br, "/geturl", getURL, account, link);
                /* Captcha required -> Free limits apply! */
                resumable = this.isResumeable(link, null);
                maxChunks = this.getMaxChunks(null);
            }
            dllink = (String) geturlResponse.get("url");
            if (StringUtils.isEmpty(dllink)) {
                this.handleErrorsAPI(account, link, this.br);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("dllink = " + dllink);
            /*
             * E.g. free = 51200, with correct Referer = 204800 --> Normal free speed: 30-50 KB/s | Free Speed with special Referer: 150-200
             * KB/s
             */
            final String rate_limitStr = new Regex(dllink, "rate_limit=(\\d+)").getMatch(0);
            if (rate_limitStr != null) {
                /* 0 = unlimited/premium */
                logger.info("Current speedlimit according to final downloadurl: " + rate_limitStr);
            }
            this.saveFreeLimit(account, currentIP, System.currentTimeMillis());
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resumable, maxChunks);
            if (!isValidDownloadConnection(dl.getConnection())) {
                logger.warning("The final dllink seems not to be a file!");
                try {
                    br.followConnection(true);
                } catch (IOException e) {
                    logger.log(e);
                }
                handleGeneralServerErrors(br, dl, account, link);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        // add download slot
        controlSlot(+1, account);
        try {
            link.setProperty(this.getDirectLinkProperty(account), dllink);
            dl.startDownload();
        } finally {
            // remove download slot
            controlSlot(-1, account);
        }
    }

    private void saveFreeLimit(final Account account, final String currentIP, final long timestampLimitActivated) {
        synchronized (CTRLLOCK) {
            if (account != null) {
                /* Account based limitation */
                account.setProperty(PROPERTY_LASTDOWNLOAD, timestampLimitActivated);
            }
            if (currentIP != null) {
                /*
                 * IP based limitation | Also relevant if user e.g. downloads via free account -> Limit will be on that account + also on
                 * anonymous download via same IP.
                 */
                final Map<String, Long> blockedIPsMap = this.getBlockedIPsMap();
                blockedIPsMap.put(currentIP, timestampLimitActivated);
                getPluginConfig().setProperty(PROPERTY_LASTDOWNLOAD, blockedIPsMap);
            }
        }
    }

    public AvailableStatus requestFileInformationWebsite(final DownloadLink link) throws Exception {
        final String fuid = getFUID(link);
        getPage("https://api." + this.getHost() + "/v1/files/" + fuid);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Map<String, Object> entries = this.restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        final String filename = (String) entries.get("name");
        // final String access = (String)entries.get("access");
        final boolean isDeleted = ((Boolean) entries.get("isDeleted")).booleanValue();
        // final boolean isAvailableForFree = ((Boolean) entries.get("isAvailableForFree")).booleanValue();
        // final boolean hasAbuse = ((Boolean) entries.get("hasAbuse")).booleanValue();
        final Object filesizeO = entries.get("size");
        // final List<Object> ressourcelist = (List<Object>) entries.get("");
        if (!StringUtils.isEmpty(filename)) {
            link.setFinalFileName(filename);
        }
        if (filesizeO != null) {
            link.setDownloadSize(Long.parseLong(filesizeO.toString()));
        }
        if (isDeleted) {
            /* Files can get deleted and filename & filesize information may still be available! */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    private String getCustomReferer(final DownloadLink link) {
        final Keep2shareConfig cfg = PluginJsonConfig.get(this.getConfigInterface());
        String custom_referer = cfg.getReferer();
        String url_referer = this.getRefererFromURL(link);
        final String sourceURL = link.getContainerUrl();
        if (!StringUtils.isEmpty(url_referer) && !cfg.isForceCustomReferer()) {
            /* Use Referer from inside added URL if given. */
            logger.info("Using referer from URL: " + url_referer);
            if (!url_referer.startsWith("http")) {
                logger.info("Applying protocol to url_referer:");
                logger.info("url_referer before: " + url_referer);
                url_referer = "https://" + url_referer;
                logger.info("url_referer after: " + url_referer);
            }
            return url_referer;
        } else if (!StringUtils.isEmpty(custom_referer)) {
            /* Use user selected Referer */
            if (!custom_referer.startsWith("http")) {
                logger.info("Applying protocol to custom_referer:");
                logger.info("custom_referer before: " + custom_referer);
                custom_referer = "https://" + custom_referer;
                logger.info("custom_referer after: " + custom_referer);
            }
            logger.info("Using custom referer: " + custom_referer);
            return custom_referer;
        } else if (!StringUtils.isEmpty(sourceURL) && !new Regex(sourceURL, this.getSupportedLinks()).matches()) {
            /*
             * Try to use source URL as Referer if it does not match any supported URL of this plugin.
             */
            logger.info("Using referer from Source-URL: " + sourceURL);
            return sourceURL;
        } else {
            /* No Referer at all. */
            return null;
        }
    }

    public Browser newWebBrowser(boolean followRedirects) {
        final Browser nbr = new Browser() {
            @Override
            public void updateCookies(Request request) {
                super.updateCookies(request);
                // sync cookies between domains!
                // just not for api requests
                if (request.getURL().getPath().contains("/api/v2")) {
                    return;
                }
                final String host = Browser.getHost(request.getUrl());
                // todo: test which reference this is.
                final Cookies cookies = request.getCookies();
                // also remove cloudflare cookies, each domain gets its own instance (same as browser)
                {
                    final Cookie cfuid = cookies.get("__cfduid");
                    if (cfuid != null) {
                        cookies.remove(cfuid);
                    }
                    final Cookie cfclearance = cookies.get("cf_clearance");
                    if (cfclearance != null) {
                        cookies.remove(cfclearance);
                    }
                }
                String[] siteSupportedNames = siteSupportedNames();
                if (siteSupportedNames() == null && request != null) {
                    siteSupportedNames = new String[] { getHost() };
                } else {
                    siteSupportedNames = new String[0];
                }
                for (final String domain : siteSupportedNames) {
                    if (domain.equals(host)) {
                        continue;
                    }
                    for (Cookie c : this.getCookies(host).getCookies()) {
                        this.setCookie(domain, c.getKey(), c.getValue());
                    }
                }
            }
        };
        nbr.setFollowRedirects(followRedirects);
        return nbr;
    }

    protected static Object REQUESTLOCK = new Object();

    public Map<String, Object> postPageRaw(final Browser ibr, String url, final Map<String, Object> postdata, final Account account) throws Exception {
        return postPageRaw(ibr, url, postdata, account, null, 0);
    }

    public Map<String, Object> postPageRaw(final Browser ibr, String url, final Map<String, Object> postdata, final Account account, final DownloadLink link) throws Exception {
        return postPageRaw(ibr, url, postdata, account, null, 0);
    }

    /**
     * general handling postPage requests! It's stable compliant with various response codes. It then passes to error handling!
     *
     * @param ibr
     * @param url
     * @param arg
     * @param account
     * @author raztoki
     * @throws Exception
     */
    public Map<String, Object> postPageRaw(final Browser ibr, String url, final Map<String, Object> postdata, final Account account, final DownloadLink link, int attempt) throws Exception {
        synchronized (REQUESTLOCK) {
            if (!StringUtils.startsWithCaseInsensitive(url, "http")) {
                url = getApiUrl() + url;
            }
            final URLConnectionAdapter con = ibr.openPostConnection(url, JSonStorage.serializeToJson(postdata));
            readConnection(con, ibr);
            antiDDoS(ibr);
            /* Only handle captchas on login page. */
            CAPTCHA loginCaptcha = null;
            final String status = PluginJSonUtils.getJsonValue(ibr, "status");
            if ("error".equalsIgnoreCase(status)) {
                if (ibr.containsHTML("\"errorCode\"\\s*:\\s*30")) {
                    /* Simple image captcha */
                    loginCaptcha = CAPTCHA.REQUESTCAPTCHA;
                } else if (ibr.containsHTML("\"errorCode\"\\s*:\\s*33")) {
                    /* reCaptcha */
                    loginCaptcha = CAPTCHA.REQUESTRECAPTCHA;
                }
            }
            if (url.endsWith("/login") && loginCaptcha != null) {
                logger.info("Login captcha attempt: " + attempt);
                if (attempt >= 1) {
                    /*
                     * Allow max 1 captcha attempt. Usually they're requesting reCaptcha so user should be able to solve it in one go.
                     */
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                // we can assume that the previous user:pass is wrong, prompt user for new one!
                final Browser cbr = new Browser();
                if (CAPTCHA.REQUESTCAPTCHA.equals(loginCaptcha)) {
                    final Map<String, Object> requestcaptcha = postPageRaw(cbr, "/requestcaptcha", new HashMap<String, Object>(), account, link);
                    final String challenge = (String) requestcaptcha.get("challenge");
                    String captcha_url = (String) requestcaptcha.get("captcha_url");
                    if (captcha_url.startsWith("https://")) {
                        logger.info("login-captcha_url is already https");
                    } else {
                        /*
                         * 2020-02-03: Possible workaround for this issues reported here: board.jdownloader.org/showthread.php?t=82989 and
                         * 2020-04-23: board.jdownloader.org/showthread.php?t=83927 and board.jdownloader.org/showthread.php?t=83781
                         */
                        logger.info("login-captcha_url is not https --> Changing it to https");
                        captcha_url = captcha_url.replace("http://", "https://");
                    }
                    if (StringUtils.isEmpty(challenge)) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else if (StringUtils.isEmpty(captcha_url)) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    // final dummy
                    final DownloadLink dummyLink = new DownloadLink(null, "Account", getInternalAPIDomain(), "https://" + getInternalAPIDomain(), true);
                    final String code = getCaptchaCode(captcha_url, dummyLink);
                    if (StringUtils.isEmpty(code)) {
                        // captcha can't be blank! Why we don't return null I don't know!
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                    }
                    postdata.put("captcha_challenge", challenge);
                    postdata.put("captcha_response", code);
                } else if (CAPTCHA.REQUESTRECAPTCHA.equals(loginCaptcha)) {
                    final Map<String, Object> requestcaptcha = postPageRaw(cbr, "/requestrecaptcha", new HashMap<String, Object>(), account, link);
                    final String challenge = (String) requestcaptcha.get("challenge");
                    String captcha_url = (String) requestcaptcha.get("captcha_url");
                    if (StringUtils.isEmpty(challenge)) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else if (StringUtils.isEmpty(captcha_url) || !captcha_url.startsWith("http")) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    if (!captcha_url.startsWith("https://")) {
                        /*
                         * 2020-02-03: Possible workaround for this issue: board.jdownloader.org/showthread.php?t=82989 and 2020-04-23:
                         * board.jdownloader.org/showthread.php?t=83927
                         */
                        logger.info("login-captcha_url is not https --> Changing it to https");
                        captcha_url = captcha_url.replace("http://", "https://");
                    }
                    cbr.getPage(captcha_url);
                    final CaptchaHelperHostPluginRecaptchaV2 rc2 = new CaptchaHelperHostPluginRecaptchaV2(this, cbr);
                    final String recaptchaV2Response = rc2.getToken();
                    if (recaptchaV2Response == null) {
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                    }
                    postdata.put("re_captcha_challenge", challenge);
                    postdata.put("re_captcha_response", recaptchaV2Response);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unsupported:" + loginCaptcha);
                }
                attempt++;
                return postPageRaw(ibr, url, postdata, account, link, attempt);
            } else {
                return handleErrorsAPI(account, link, ibr);
            }
        }
    }

    /**
     * @author razotki
     * @author jiaz
     * @param con
     * @param ibr
     * @throws IOException
     * @throws PluginException
     */
    private void readConnection(final URLConnectionAdapter con, final Browser ibr) throws IOException, PluginException {
        final Request request;
        if (con.getRequest() != null) {
            request = con.getRequest();
        } else {
            request = ibr.getRequest();
        }
        if (con.getRequest() != null && con.getRequest().getHtmlCode() != null) {
            return;
        } else if (con.getRequest() != null && !con.getRequest().isRequested()) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Request not sent yet!");
        } else if (!con.isConnected()) {
            // getInputStream/getErrorStream call connect!
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Connection is not connected!");
        }
        final byte[] responseBytes;
        final InputStream is = getInputStream(con, ibr);
        try {
            request.setReadLimit(ibr.getLoadLimit());
            responseBytes = Request.read(con, request.getReadLimit());
            request.setResponseBytes(responseBytes);
        } finally {
            is.close();
        }
        LogInterface log = ibr.getLogger();
        if (log == null) {
            log = logger;
        }
        log.fine("\r\n" + request.getHtmlCode());
        if (request.isKeepByteArray() || ibr.isKeepResponseContentBytes()) {
            request.setKeepByteArray(true);
            request.setResponseBytes(responseBytes);
        }
    }

    protected InputStream getInputStream(final URLConnectionAdapter con, final Browser br) throws IOException {
        final int responseCode = con.getResponseCode();
        switch (responseCode) {
        case 502:
            // Bad Gateway
            break;
        case 542:
            // A timeout occurred
            break;
        default:
            con.setAllowedResponseCodes(new int[] { responseCode });
            break;
        }
        return con.getInputStream();
    }

    private static enum CAPTCHA {
        REQUESTCAPTCHA,
        REQUESTRECAPTCHA
    }

    protected String getAuthToken(final Account account, final DownloadLink link, final boolean validate) throws Exception {
        synchronized (account) {
            final String storedAuthToken = account.getStringProperty(PROPERTY_ACCOUNT_AUTHTOKEN);
            if (storedAuthToken != null) {
                if (!validate) {
                    /* Return token without checking */
                    return storedAuthToken;
                }
                try {
                    getAccountInfoViaAPI(account, br, storedAuthToken);
                    logger.info("Validated existing auth_token");
                    return storedAuthToken;
                } catch (final Exception ignore) {
                    this.dumpAuthToken(account);
                    logger.info("Failed to validate existing auth_token -> Full login required");
                }
            }
            logger.info("Performing full login");
            // we don't want to pollute this.br
            final Browser auth = prepBrowser(new Browser());
            final HashMap<String, Object> loginJson = new HashMap<String, Object>();
            loginJson.put("username", account.getUser());
            loginJson.put("password", account.getPass());
            final Map<String, Object> loginResponse = postPageRaw(auth, "/login", loginJson, account, link);
            final String freshAuthToken = (String) loginResponse.get("auth_token");
            if (StringUtils.isEmpty(freshAuthToken)) {
                /* This should never happen */
                account.removeProperty(PROPERTY_ACCOUNT_AUTHTOKEN);
                throw new AccountInvalidException("Fatal: Token missing");
            } else {
                logger.info("new auth_token: " + freshAuthToken);
                account.setProperty(PROPERTY_ACCOUNT_AUTHTOKEN, freshAuthToken);
            }
            return freshAuthToken;
        }
    }

    protected Map<String, Object> handleErrorsAPI(final Account account, final DownloadLink link, final Browser br) throws PluginException {
        if (br != null && br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 400) {
            /* 2019-07-17: This may happen after any request even if the request itself is done right. */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 400", 5 * 60 * 1000l);
        } else if (br != null && br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 429) {
            /* 2019-07-23: This may happen after any request e.g. after '/requestcaptcha' RE log 4772186935451 */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 429 - too many requests", 3 * 60 * 1000l);
        }
        try {
            final Map<String, Object> entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
            /* E.g. {"message":"Invalid login or password","status":"error","code":406,"errorCode":70} */
            final String status = (String) entries.get("status");
            Number errCode = (Number) entries.get("errorCode");
            // if (errCode == null && isSubErrors) {
            // // subErrors
            // errCode = (Number) entries.get("code");
            // }
            if (errCode == null) {
                // subErrors
                errCode = (Number) entries.get("code");
            }
            if (errCode == null) {
                /* No errors */
                return entries;
            }
            int err = errCode.intValue();
            if (err == 200 && StringUtils.equalsIgnoreCase(status, "success")) {
                /*
                 * No error e.g.
                 * {"status":"success","code":200,"challenge":"blabla","captcha_url":"http://k2s.cc/api/v2/reCaptcha.html?id=blabla"}
                 */
                return entries;
            }
            String serversideErrormessage = (String) entries.get("message");
            String timeRemaining = null;
            final List<Map<String, Object>> subErrorsList = (List<Map<String, Object>>) entries.get("errors");
            /* For some errors, we prefer to handle the subError(s) TODO: Remove/simplify this. */
            if (err == 21 || err == 22 || err == 42) {
                if (subErrorsList != null && !subErrorsList.isEmpty()) {
                    final Map<String, Object> subError0 = subErrorsList.get(0);
                    err = ((Number) subError0.get("code")).intValue();
                    /* Can be missing -> null */
                    serversideErrormessage = (String) subError0.get("message");
                    timeRemaining = (String) subError0.get("timeRemaining");
                }
            }
            String msgForUser = getErrorMessageForUser(err);
            if (StringUtils.isEmpty(msgForUser)) {
                /* No language String available for errormessage? Fallback to provided errormessage */
                msgForUser = serversideErrormessage;
            }
            try {
                switch (err) {
                case 1:
                    // DOWNLOAD_COUNT_EXCEEDED = 1; "Download count files exceed"
                    // assume non account/free account
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msgForUser);
                case 2:
                    // {"message":"Invalid request params","status":"error","code":400,"errorCode":2}
                    // IP temp. blocked on login
                    // DOWNLOAD_TRAFFIC_EXCEEDED = 2; "Traffic limit exceed"
                    // assume all types
                    if (account != null) {
                        throw new AccountUnavailableException(msgForUser, 15 * 60 * 1000l);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msgForUser);
                    }
                case 3:
                case 7:
                    // DOWNLOAD_FILE_SIZE_EXCEEDED = 3; "Free user can't download large files. Upgrade to PREMIUM and forget about limits."
                    // PREMIUM_ONLY = 7; "This download available only for premium users"
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":7}]}
                    premiumDownloadRestriction(msgForUser);
                case 4:
                    // DOWNLOAD_NO_ACCESS = 4; "You no can access to this file"
                    // not sure about this...
                    throw new PluginException(LinkStatus.ERROR_FATAL, msgForUser);
                case 5:
                    // DOWNLOAD_WAITING = 5; "Please wait to download this file"
                    // {"message":"Download not
                    // available","status":"error","code":406,"errorCode":42,"errors":[{"code":5,"timeRemaining":"2521.000000"}]}
                    // think timeRemaining is in seconds
                    if (!StringUtils.isEmpty(timeRemaining) && timeRemaining.matches("[\\d\\.]+")) {
                        final String time = timeRemaining.substring(0, timeRemaining.indexOf("."));
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msgForUser, Integer.parseInt(time) * 1000);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msgForUser, 15 * 60 * 1000);
                    }
                case 6:
                    // DOWNLOAD_FREE_THREAD_COUNT_TO_MANY = 6; "Free account does not allow to download more than one file at the same time"
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msgForUser);
                case 9:
                    /*
                     * {"status":"error","code":406,"message":"Download is not available","errorCode":21,"errors":[{"code":9,
                     * "message":"This download available only for store subscribers"}]}
                     */
                    throw new AccountRequiredException();
                case 8:
                    // PRIVATE_ONLY = 8; //'This is private file',
                    privateDownloadRestriction(msgForUser);
                case 10:
                    // Bad/invalid token? {"message":"You are not authorized for this action","status":"error","code":403,"errorCode":10}
                    dumpAuthToken(account);
                    /* Token is expired -> Do not permanently disable account as login credentials can still be valid. */
                    throw new AccountUnavailableException(msgForUser, 1 * 60 * 1000l);
                case 11:
                case 42:
                    /* 2020-11-26: E.g. "File is available for premium users only" AFTER captcha in free mode. */
                    /* Old comments below */
                    // ERROR_NEED_WAIT_TO_FREE_DOWNLOAD = 41;
                    // ERROR_DOWNLOAD_NOT_AVAILABLE = 42;
                    // {"message":"Download is not
                    // available","status":"error","code":406,"errorCode":21,"errors":[{"code":2,"message":"Traffic limit exceed"}]}
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":3}]}
                    // {"message":"Download not
                    // available","status":"error","code":406,"errorCode":42,"errors":[{"code":5,"timeRemaining":"2521.000000"}]}
                    // {"message":"Download is not available","status":"error","code":406,"errorCode":42,"errors":[{"code":6,"message":"
                    // Free account does not allow to download more than one file at the same time"}]}
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":6}]}
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":7}]}
                    // sub error, pass it back into itself.
                    throw new AccountRequiredException();
                case 75:
                    // ERROR_YOU_ARE_NEED_AUTHORIZED = 10;
                    // ERROR_AUTHORIZATION_EXPIRED = 11;
                    // ERROR_ILLEGAL_SESSION_IP = 75;
                    // {"message":"This token not allow access from this IP address","status":"error","code":403,"errorCode":75}
                    // this should never happen, as its handled within postPage and auth_token should be valid for download
                    dumpAuthToken(account);
                    throw new AccountUnavailableException(msgForUser, 1 * 60 * 1000l);
                case 20:
                    // ERROR_FILE_NOT_FOUND = 20;
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, msgForUser);
                case 21:
                case 22:
                    // ERROR_FILE_IS_NOT_AVAILABLE = 21;
                    // {"message":"Download is not
                    // available","status":"error","code":406,"errorCode":21,"errors":[{"code":2,"message":"Traffic limit exceed"}]}
                    // sub error, pass it back into itself.
                    // ERROR_FILE_IS_BLOCKED = 22;
                    // what does this mean? premium only link ? treating as 'file not found'
                    /* 2020-01-29: {"status":"error","code":406,"message":"File is blocked","errorCode":22} */
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, msgForUser);
                case 23:
                    // {"message":"file_id is folder","status":"error","code":406,"errorCode":23}
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, msgForUser);
                case 33:
                    // CAPTCHA.REQUESTRECAPTCHA
                case 30:
                    // ERROR_CAPTCHA_REQUIRED = 30;
                    // this shouldn't happen in dl method.. beware website can contain captcha on login, api not of yet.
                    if (link == null && account != null) {
                        // {"message":"You need send request for free download with captcha
                        // fields","status":"error","code":406,"errorCode":30}
                        // false positive for invalid auth_token (work around)! dump cookies and retry
                        dumpAuthToken(account);
                        throw new AccountUnavailableException(msgForUser, 1 * 60 * 1000l);
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                case 31:
                    // ERROR_CAPTCHA_INVALID = 31;
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA, msgForUser);
                case 40:
                    // ERROR_WRONG_FREE_DOWNLOAD_KEY = 40;
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, msgForUser);
                case 41:
                case 70:
                case 72:
                    // ERROR_INCORRECT_USERNAME_OR_PASSWORD = 70;
                    // ERROR_ACCOUNT_BANNED = 72;
                    dumpAuthToken(account);
                    throw new AccountInvalidException(msgForUser);
                case 71:
                    // ERROR_LOGIN_ATTEMPTS_EXCEEDED = 71;
                    // This is actually an IP restriction!
                    // 30min wait time.... since wait time isn't respected (throw new PluginException(LinkStatus.ERROR_PREMIUM, msg, time)),
                    // we need to set value like this and then throw temp disable.
                    // new one
                    // {"message":"Login attempt was exceed, please wait or verify your request via captcha
                    // challenge","status":"error","code":406,"errorCode":71}
                    throw new AccountUnavailableException(msgForUser, 31 * 60 * 1000l);
                case 73:
                    // ERROR_NO_ALLOW_ACCESS_FROM_NETWORK = 73;
                    if (account != null) {
                        throw new AccountUnavailableException("No allow access from network", 6 * 60 * 60 * 1000l);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_FATAL, msgForUser);
                    }
                case 74:
                    // ERROR_UNKNOWN_LOGIN_ERROR = 74;
                    if (account != null) {
                        throw new AccountInvalidException("Account has been banned");
                    } else {
                        throw new PluginException(LinkStatus.ERROR_FATAL, msgForUser);
                    }
                case 76:
                    // ERROR_ACCOUNT_STOLEN = 76;
                    throw new AccountInvalidException(msgForUser);
                default:
                    logger.warning("Unknown errorcode: " + err);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } catch (final PluginException p) {
                logger.warning(getRevisionInfo());
                logger.warning("ERROR :: " + msgForUser);
                throw p;
            }
        } catch (final JSonMapperException jse) {
            final String msg = "Invalid API response";
            if (account != null) {
                throw new AccountUnavailableException(msg, 3 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, msg, 3 * 60 * 1000l);
            }
        }
    }

    /**
     * Provides translation service
     *
     * @param code
     * @return
     */
    private String getErrorMessageForUser(final int code) {
        String msg = null;
        if ("de".equalsIgnoreCase(lng)) {
            if (code == 1) {
                msg = "Du hast die maximale Anzahl an Dateien heruntergeladen.";
            } else if (code == 2) {
                msg = "Traffic limit erreicht!";
            } else if (code == 3) {
                msg = "Dateigrößenlimitierung";
            } else if (code == 4) {
                msg = "Du hast keinen Zugriff auf diese Datei!";
            } else if (code == 5) {
                msg = "Wartezeit entdeckt!";
            } else if (code == 6) {
                msg = "Maximale Anzahl paralleler Downloads erreicht!";
            } else if (code == 7) {
                msg = "Zugriffsbeschränkung - Nur Premiumbenutzer können diese Datei herunterladen!";
            } else if (code == 8) {
                msg = "Zugriffsbeschränkung - Nur der Besitzer dieser Datei darf sie herunterladen!";
            } else if (code == 10) {
                msg = "Du bist nicht berechtigt!";
            } else if (code == 11) {
                msg = "auth_token is abgelaufen!";
            } else if (code == 21 || code == 42) {
                msg = "Download momentan nicht möglich! Genereller Fehlercode mit Sub-Code!";
            } else if (code == 23) {
                msg = "Das ist ein Ordner - du kannst keine Ordner als Datei herunterladen!";
            } else if (code == 30) {
                msg = "Captcha benötigt!";
            } else if (code == 33) {
                msg = "ReCaptcha benötigt!";
            } else if (code == 31) {
                msg = "Ungültiges Captcha";
            } else if (code == 40) {
                msg = "Falscher download key";
            } else if (code == 41) {
                msg = "Wartezeit entdeckt!";
            } else if (code == 70) {
                msg = "Ungültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.";
            } else if (code == 71) {
                msg = "Zu viele Loginversuche!";
            } else if (code == 72) {
                msg = "Dein Account wurde gesperrt!";
            } else if (code == 73) {
                msg = "Du kannst dich mit deiner aktuellen Verbindung nicht zu " + getInternalAPIDomain() + " verbinden!";
            } else if (code == 74) {
                msg = "Unbekannter Login Fehler!";
            }
        } else if ("pt".equalsIgnoreCase(lng)) {
            if (code == 1) {
                msg = "Já descarregou a quantidade máxima de arquivos!";
            } else if (code == 2) {
                msg = "Limite de tráfego alcançado!";
            } else if (code == 3) {
                msg = "Limite do tamanho do ficheiro";
            } else if (code == 4) {
                msg = "Sem acesso a este ficheiro!";
            } else if (code == 5) {
                msg = "Detetado tempo de espera!";
            } else if (code == 6) {
                msg = "Alcançado o limite máximo de descargas paralelas!";
            } else if (code == 7) {
                msg = "Acesso Restrito - Só os possuidores de Contas Premium podem efetuar a descarga deste ficheiro!";
            } else if (code == 8) {
                msg = "Acesso Restrito - Só o proprietário deste ficheiro pode fazer esta descarga!";
            } else if (code == 10) {
                msg = "Não tem autorização!";
            } else if (code == 11) {
                msg = "auth_token - expirou!";
            } else if (code == 21 || code == 42) {
                msg = "Não é possível fazer a descarga! Erro no Código genérico da Sub-rotina!";
            } else if (code == 23) {
                msg = "Esta ligação (URL) é uma Pasta. Não pode descarregar a pasta como ficheiro!";
            } else if (code == 30 || code == 33) {
                msg = "Inserir Captcha!";
            } else if (code == 31) {
                msg = "Captcha inválido";
            } else if (code == 40) {
                msg = "Chave de descarga inválida";
            } else if (code == 41) {
                msg = "Detetado tempo de espera!";
            } else if (code == 70) {
                msg = "Invalido username/password!\r\nTem a certeza que o username e a password que introduziu estao corretos? Algumas dicas:\r\n1. Se a password contem caracteres especiais, altere (ou elimine) e tente novamente!\r\n2. Digite o username/password manualmente, não use copiar e colar.";
            } else if (code == 71) {
                msg = "Tentou esta ligação vezes demais!";
            } else if (code == 72) {
                msg = "A sua conta foi banida!";
            } else if (code == 73) {
                msg = "Não pode aceder " + getInternalAPIDomain() + " a partir desta ligação de NET!";
            } else if (code == 74) {
                msg = "Erro, Login desconhecido!";
            }
        } else if ("es".equalsIgnoreCase(lng)) {
            if (code == 1) {
                msg = "¡Ha descargado la cantidad máxima de archivos!";
            } else if (code == 2) {
                msg = "¡Límite de tráfico alcanzado!";
            } else if (code == 3) {
                msg = "Limitación del tamaño del archivo";
            } else if (code == 4) {
                msg = "¡No hay acceso a este archivo!";
            } else if (code == 5) {
                msg = "¡Tiempo de espera detectado!";
            } else if (code == 6) {
                msg = "¡Se alcanzó el número máximo de descargas paralelas!";
            } else if (code == 7) {
                msg = "Restricción de acceso - ¡Sólo los titulares de las cuentas premium pueden descargar este archivo!";
            } else if (code == 8) {
                msg = "Restricción de acceso - ¡La descarga Sólo está permitida para el propietario de este archivo!";
            } else if (code == 10) {
                msg = "¡No está autorizado!";
            } else if (code == 11) {
                msg = "¡auth_token ha expirado!";
            } else if (code == 21 || code == 42) {
                msg = "No es posible realizar la descarga en este momento. ¡Código de error genérico con subcódigo!";
            } else if (code == 23) {
                msg = "Este enlace es un folder. ¡Usted no puede descargar este folder como archivo!";
            } else if (code == 30 || code == 33) {
                msg = "Captcha requerida!";
            } else if (code == 31) {
                msg = "Captcha inválido";
            } else if (code == 40) {
                msg = "Clave de descarga incorrecta";
            } else if (code == 41) {
                msg = "¡Tiempo de espera detectado!";
            } else if (code == 70) {
                msg = "Usario/Contraseña inválida\r\n¿Está seguro que el usuario y la contraseña ingresados son correctos? Algunos consejos:\r\n1. Si su contraseña contiene carácteres especiales, cambiela (remuevala) e intente de nuevo.\r\n2. Escriba su usuario/contraseña manualmente en lugar de copiar y pegar.";
            } else if (code == 71) {
                msg = "¡Usted ha intentado iniciar sesión demasiadas veces!";
            } else if (code == 72) {
                msg = "¡Su cuenta ha sido baneada!";
            } else if (code == 73) {
                msg = "¡Usted no puede acceder " + getInternalAPIDomain() + " desde su conexión de red actual!";
            } else if (code == 74) {
                msg = "¡Error de inicio de sesión desconocido!";
            }
        } else if ("pl".equalsIgnoreCase(lng)) {
            if (code == 1) {
                msg = "Pobrałeś maksymalną liczbę plików.";
            } else if (code == 2) {
                msg = "Osiągnięto limit ruchu!";
            } else if (code == 3) {
                msg = "Ograniczenie na rozmiar plików";
            } else if (code == 4) {
                msg = "Brak dostêpu do pliku!";
            } else if (code == 5) {
                msg = "Wykryto czas oczekiwania!";
            } else if (code == 6) {
                msg = "Osiągnięto maksymalną liczbę równoległych pobrań!";
            } else if (code == 7) {
                msg = "Ograniczenie dostępu - tylko użytkownicy premium mogą pobrać ten plik!!";
            } else if (code == 8) {
                msg = "Ograniczenie dostępu - tylko właściciel tego pliku może go pobrać!!";
            } else if (code == 10) {
                msg = "Nie masz uprawnień!";
            } else if (code == 11) {
                msg = "token autoryzacji wygasł!";
            } else if (code == 21 || code == 42) {
                msg = "Pobieranie obecnie niemożliwe! Ogólny kod błędu z subkodem!";
            } else if (code == 23) {
                msg = "To jest folder - nie możesz pobrać folderu jako pliku!";
            } else if (code == 30 || code == 33) {
                msg = "Potrzebny Captcha!";
            } else if (code == 31) {
                msg = "Nieprawidłowy captcha";
            } else if (code == 40) {
                msg = "Niepoprawny klucz pobierania";
            } else if (code == 41) {
                msg = "Wykryto czas oczekiwania!";
            } else if (code == 70) {
                msg = "Nieprawidłowa nazwa użytkownika lub hasło!\r\nJesteś pewien, że wprowadzona nazwa użytkownika i hasło są poprawne? Spróbuj wykonać następujące czynności:\r\n1. Jeśli hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź dane logowania ręcznie (bez kopiowania / wklejania).";
            } else if (code == 71) {
                msg = "Zbyt wiele prób logowania!";
            } else if (code == 72) {
                msg = "Twoje konto zostało zablokowane!";
            } else if (code == 73) {
                msg = "Nie możesz połączyć się z " + getInternalAPIDomain() + " przy obecnym połączeniu!";
            } else if (code == 74) {
                msg = "Nieznany błąd logowania!";
            }
        }
        if (StringUtils.isEmpty(msg)) {
            // default english!
            if (code == 1) {
                msg = "You've downloaded the maximum amount of files!";
            } else if (code == 2) {
                msg = "Traffic limit reached!";
            } else if (code == 3) {
                msg = "File size limitation";
            } else if (code == 4) {
                msg = "No access to this file!";
            } else if (code == 5) {
                msg = "Wait time detected!";
            } else if (code == 6) {
                msg = "Maximum number parallel downloads reached!";
            } else if (code == 7) {
                msg = "Access Restriction - Only premium account holders can download this file!";
            } else if (code == 8) {
                msg = "Access Restriction - Only the owner of this file is allowed to download!";
            } else if (code == 10) {
                msg = "Your not authorised req: auth_token!";
            } else if (code == 11) {
                msg = "auth_token has expired!";
            } else if (code == 21 || code == 42) {
                msg = "Download not possible at this time! Generic Error code with subcode!";
            } else if (code == 23) {
                msg = "This URL is a Folder, you can not download folder as a file!";
            } else if (code == 30) {
                msg = "Captcha required!";
            } else if (code == 33) {
                msg = "Recaptcha required!";
            } else if (code == 31) {
                msg = "Invalid Captcha";
            } else if (code == 40) {
                msg = "Wrong download key";
            } else if (code == 41) {
                msg = "Wait time detected!";
            } else if (code == 70) {
                msg = "Invalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.";
            } else if (code == 71) {
                msg = "You've tried logging in too many times!";
            } else if (code == 72) {
                msg = "Your account has been banned!";
            } else if (code == 73) {
                msg = "You can not access " + getInternalAPIDomain() + " from your current network connection!";
            } else if (code == 74) {
                msg = "Unknown login error!";
            } else if (code == 75) {
                msg = "Illegal IP Session";
            } else if (code == 76) {
                msg = "Stolen Account";
            }
        }
        return msg;
    }

    /**
     * When premium only download restriction (eg. filesize), throws exception with given message
     *
     * @param msg
     * @throws PluginException
     */
    public void premiumDownloadRestriction(final String msg) throws PluginException {
        throw new AccountRequiredException(msg);
    }

    /**
     * Execute this for files which can only be downloaded by the owner.
     *
     * @param msg
     * @throws PluginException
     */
    public void privateDownloadRestriction(final String msg) throws PluginException {
        throw new PluginException(LinkStatus.ERROR_FATAL, msg);
    }

    private void dumpAuthToken(final Account account) throws PluginException {
        if (account == null) {
            /* Developer mistake */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        synchronized (account) {
            final String currentToken = account.getStringProperty(PROPERTY_ACCOUNT_AUTHTOKEN);
            if (currentToken != null) {
                logger.info("dumpAuthToken:" + currentToken);
                account.removeProperty(PROPERTY_ACCOUNT_AUTHTOKEN);
            } else {
                logger.warning("No token there to be dumped");
            }
        }
    }

    protected void handleGeneralServerErrors(final Browser br, final DownloadInterface dl, final Account account, final DownloadLink link) throws PluginException {
        if (br.containsHTML("(?i)You exceeded your Premium \\d+ GB daily limit, try to download tomorrow")) {
            if (account != null) {
                throw new AccountUnavailableException("You exceeded your Premium daily limit, try to download tomorrow", 60 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        final String alreadyDownloading = "Your current tariff? doesn't allow to download more files then you are downloading now";
        if (br.containsHTML(alreadyDownloading)) {
            // most likely transparent proxy/carrier NAT/multiple WAN IPs
            // HTTP/1.1 504 Gateway Time-out
            // <head>
            // <title>Download unavailable</title>
            // </head>
            // <body>
            // Your current tariff doesn't allow to download more files then you are downloading now.
            // If you are a free user, please upgrade to premium.
            // </body>
            // </html>
            // We also only have 1 max free sim currently, if we go higher we need to track current transfers against
            // connection_candidate(proxy|direct) IP address, and reduce max sim by one.
            if (account == null || account.getType() == AccountType.FREE) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, alreadyDownloading, 15 * 60 * 1000);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, alreadyDownloading, 15 * 60 * 1000);
            }
        } else if (dl.getConnection().getResponseCode() == 401) {
            // we should never get this here because checkcheckDirectLink should pick it up.
            // this happens when link is old, and site then prompts with basicauth which is under 401 header.
            // ----------------Response------------------------
            // HTTP/1.1 401 Unauthorized
            // Server: nginx/1.4.6 (Ubuntu)
            // Date: Fri, 29 Aug 2014 08:07:54 GMT
            // Content-Type: text/plain
            // Content-Length: 35
            // Connection: close
            // Www-Authenticate: Swift realm="AUTH_system"
            link.removeProperty(this.getDirectLinkProperty(account));
            throw new PluginException(LinkStatus.ERROR_RETRY);
        } else if (dl.getConnection().getResponseCode() == 404 || br.containsHTML("(?i)>\\s*Not Found<")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        } else if (dl.getConnection().getResponseCode() == 503) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 503", 5 * 60 * 1000l);
        } else if (dl.getConnection().getResponseCode() == 429) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Too many request in short succession");
        } else if (dl.getConnection().getResponseCode() == 500 || br.containsHTML("An error occurred")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error occurred", 15 * 60 * 1000l);
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return reqFileInformation(link);
    }

    private AvailableStatus reqFileInformation(final DownloadLink link) throws Exception {
        final boolean checked = checkLinks(new DownloadLink[] { link });
        // we can't throw exception in checklinks! This is needed to prevent multiple captcha events!
        if (!checked && hasAntiddosCaptchaRequirement()) {
            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
        } else if (!checked || !link.isAvailabilityStatusChecked()) {
            link.setAvailableStatus(AvailableStatus.UNCHECKABLE);
        } else if (!link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return link.getAvailableStatus();
    }

    private String getLanguage() {
        try {
            return org.appwork.txtresource.TranslationFactory.getDesiredLocale().getLanguage().toLowerCase(Locale.ENGLISH);
        } catch (final Throwable ignore) {
            return System.getProperty("user.language");
        }
    }

    private static Object CTRLLOCK = new Object();

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param controlSlot
     *            (+1|-1)
     * @author raztoki
     */
    protected abstract Map<String, Object> getHostMap();

    protected <T> T getHostMapEntry(final String key, Type T, T defaultValue) {
        final Map<String, Object> map = getHostMap();
        synchronized (map) {
            Object ret = map.get(key);
            if (ret == null) {
                ret = defaultValue;
                map.put(key, ret);
            }
            return (T) ret;
        }
    }

    private Map<String, Long> getBlockedIPsMap() {
        return getHostMapEntry("blockedIPsHostMap", HashMap.class, new HashMap<String, Long>());
    }

    private void controlSlot(final int num, final Account account) {
        if (isNoAccountOrFreeAccount(account)) {
            synchronized (freeDownloadHandling) {
                final AbstractProxySelectorImpl proxySelector = getDownloadLink().getDownloadLinkController().getProxySelector();
                AtomicLong[] store = freeDownloadHandling.get(proxySelector);
                if (store == null) {
                    store = new AtomicLong[] { new AtomicLong(-1), new AtomicLong(0) };
                    freeDownloadHandling.put(proxySelector, store);
                }
                if (num == 1) {
                    store[0].set(System.currentTimeMillis());
                    store[1].incrementAndGet();
                } else if (num == -1) {
                    store[1].decrementAndGet();
                }
            }
        }
        synchronized (CTRLLOCK) {
            final boolean useAccountMaps = account != null;
            final String slotID = useAccountMaps ? "Account:" + account.getUser() : "FREE";
            final AtomicInteger slotsInUse = useAccountMaps ? getAccountSlotsInUse(account) : getFreeSlotsInUse();
            /* Slots will be either decreased or increased by one */
            final AtomicInteger maxSlots = useAccountMaps ? getHostMaxAccount(account) : getHostMaxFree();
            final int slotsInUseOld = slotsInUse.get();
            final int slotsInUseNew = slotsInUseOld + num;
            slotsInUse.set(slotsInUseNew);
            /* Decrease/increase max simultaneous downloads/"slots". */
            final int maxSlotsPossible = useAccountMaps ? Math.max(1, account.getMaxSimultanDownloads()) : PluginJsonConfig.get(this.getConfigInterface()).getMaxSimultaneousFreeDownloads();
            maxSlots.getAndSet(Math.min(slotsInUse.get() + 1, maxSlotsPossible));
            logger.info(slotID + ":maxSlotsUsed was=" + slotsInUseOld + "|now = " + slotsInUseNew + "|max=" + maxSlots.get());
        }
    }

    protected static WeakHashMap<AbstractProxySelectorImpl, AtomicLong[]> freeDownloadHandling         = new WeakHashMap<AbstractProxySelectorImpl, AtomicLong[]>();
    private final long                                                    nextFreeDownloadSlotInterval = 2 * 60 * 60 * 1000l;

    @Override
    public int getMaxSimultanDownload(final DownloadLink link, final Account account, final AbstractProxySelectorImpl proxy) {
        /*
         * TODO: Always use getHostMaxAccount(account) for accounts (no matter which type) and getHostMaxFree for non account downloads(?)
         */
        if (isNoAccountOrFreeAccount(account)) {
            final AtomicLong[] store;
            synchronized (freeDownloadHandling) {
                store = freeDownloadHandling.get(proxy);
            }
            if (store != null) {
                final long freeDownloadsRunning = store[1].get();
                if (freeDownloadsRunning > 0 && System.currentTimeMillis() - store[0].get() > nextFreeDownloadSlotInterval) {
                    return Math.min((int) freeDownloadsRunning + 1, 20);
                }
            }
        }
        return super.getMaxSimultanDownload(link, account, proxy);
    }

    protected AtomicInteger getAccountSlotsInUse(final Account account) {
        return getHostMapEntry("accountSlotsInUse_" + account.getId(), AtomicInteger.class, new AtomicInteger(0));
    }

    protected AtomicInteger getFreeSlotsInUse() {
        return getHostMapEntry("freeSlotsInUse", AtomicInteger.class, new AtomicInteger(0));
    }

    protected AtomicInteger getHostMaxAccount(final Account account) {
        return getHostMapEntry("maxAccount_" + account.getId(), AtomicInteger.class, new AtomicInteger(1));
    }

    protected AtomicInteger getHostMaxFree() {
        return getHostMapEntry("maxFree", AtomicInteger.class, new AtomicInteger(1));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return this.getHostMaxFree().get();
    }

    protected final boolean isNoAccountOrFreeAccount(final Account account) {
        if (account == null || Account.AccountType.FREE.equals(account.getType())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isSameAccount(Account downloadAccount, AbstractProxySelectorImpl downloadProxySelector, Account candidateAccount, AbstractProxySelectorImpl candidateProxySelector) {
        if (downloadProxySelector == candidateProxySelector) {
            if (isNoAccountOrFreeAccount(downloadAccount) && isNoAccountOrFreeAccount(candidateAccount)) {
                return true;
            }
        }
        return super.isSameAccount(downloadAccount, downloadProxySelector, candidateAccount, candidateProxySelector);
    }

    private long getPluginSavedLastDownloadTimestamp(final String currentIP) {
        final Map<String, Long> blockedIPsHostMap = getBlockedIPsMap();
        synchronized (blockedIPsHostMap) {
            final Map<String, Long> blockedIPsMap = this.getBlockedIPsMap();
            final Iterator<Entry<String, Long>> it = blockedIPsMap.entrySet().iterator();
            while (it.hasNext()) {
                final Entry<String, Long> ipentry = it.next();
                final String ip = ipentry.getKey();
                final long timestamp = ipentry.getValue();
                if (System.currentTimeMillis() - timestamp >= FREE_RECONNECTWAIT_MILLIS) {
                    /* Remove old entries */
                    it.remove();
                }
                if (ip.equals(currentIP)) {
                    return timestamp;
                }
            }
        }
        return 0;
    }

    // cloudflare
    /**
     * Gets page <br />
     * - natively supports silly cloudflare anti DDoS crapola
     *
     * @author raztoki
     */
    public void getPage(final Browser ibr, final String page) throws Exception {
        if (page == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        URLConnectionAdapter con = null;
        try {
            con = ibr.openGetConnection(page);
            readConnection(con, ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        antiDDoS(ibr);
    }

    /**
     * Wrapper into getPage(importBrowser, page), where browser = br;
     *
     * @author raztoki
     *
     */
    public void getPage(final String page) throws Exception {
        getPage(br, page);
    }

    public void postPage(final Browser ibr, String page, final String postData) throws Exception {
        if (page == null || postData == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        final Request request = ibr.createPostRequest(page, postData);
        request.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        URLConnectionAdapter con = null;
        try {
            con = ibr.openRequestConnection(request);
            readConnection(con, ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        antiDDoS(ibr);
    }

    /**
     * Wrapper into postPage(importBrowser, page, postData), where browser == this.br;
     *
     * @author raztoki
     *
     */
    public void postPage(String page, final String postData) throws Exception {
        postPage(br, page, postData);
    }

    public void sendForm(final Browser ibr, final Form form) throws Exception {
        if (form == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        boolean setContentType = false;
        if (Form.MethodType.POST.equals(form.getMethod())) {
            // if the form doesn't contain an action lets set one based on current br.getURL().
            if (form.getAction() == null || form.getAction().equals("")) {
                form.setAction(ibr.getURL());
            }
            setContentType = true;
        }
        final Request request = ibr.createFormRequest(form);
        if (setContentType) {
            request.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        }
        URLConnectionAdapter con = null;
        try {
            con = ibr.openRequestConnection(request);
            readConnection(con, ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        antiDDoS(ibr);
    }

    /**
     * Wrapper into sendForm(importBrowser, form), where browser == this.br;
     *
     * @author raztoki
     *
     */
    public void sendForm(final Form form) throws Exception {
        sendForm(br, form);
    }

    public void sendRequest(final Browser ibr, final Request request) throws Exception {
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        URLConnectionAdapter con = null;
        try {
            con = ibr.openRequestConnection(request);
            readConnection(con, ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        antiDDoS(ibr);
    }

    /**
     * Wrapper into sendRequest(importBrowser, form), where browser == this.br;
     *
     * @author raztoki
     *
     */
    public void sendRequest(final Request request) throws Exception {
        sendRequest(br, request);
    }

    private int     a_responseCode429    = 0;
    private int     a_responseCode5xx    = 0;
    private boolean a_captchaRequirement = false;

    protected final boolean hasAntiddosCaptchaRequirement() {
        return a_captchaRequirement;
    }

    protected final AtomicInteger antiDDosCaptcha = new AtomicInteger(0);

    @Override
    public void setHasCaptcha(DownloadLink link, Account acc, Boolean hasCaptcha) {
        if (antiDDosCaptcha.get() == 0) {
            super.setHasCaptcha(link, acc, hasCaptcha);
        }
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final jd.plugins.Account acc) {
        if (acc == null || acc.getType() == AccountType.FREE) {
            /* No account or free account, yes we can expect captcha */
            return true;
        } else {
            /* Only sometimes required during login */
            return false;
        }
    }

    /**
     * Performs Cloudflare and Incapsula requirements.<br />
     * Auto fill out the required fields and updates antiDDoSCookies session.<br />
     * Always called after Browser Request!
     *
     * @version 0.03
     * @author raztoki
     **/
    private void antiDDoS(final Browser ibr) throws Exception {
        if (ibr == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final HashMap<String, String> cookies = new HashMap<String, String>();
        if (ibr.getHttpConnection() != null) {
            final int responseCode = ibr.getHttpConnection().getResponseCode();
            // if (requestHeadersHasKeyNValueContains(ibr, "server", "cloudflare-nginx")) {
            if (containsCloudflareCookies(ibr)) {
                final Form cloudflareFormt = getCloudflareChallengeForm(ibr);
                if (responseCode == 403 && cloudflareFormt != null) {
                    a_captchaRequirement = true;
                    // recapthcha v2
                    if (CaptchaHelperHostPluginRecaptchaV2.containsRecaptchaV2Class(cloudflareFormt)) {
                        antiDDosCaptcha.incrementAndGet();
                        try {
                            final DownloadLink dllink = new DownloadLink(null, (this.getDownloadLink() != null ? this.getDownloadLink().getName() + " :: " : "") + "antiDDoS Provider 'Clouldflare' requires Captcha", this.getHost(), "http://" + this.getHost(), true);
                            this.setDownloadLink(dllink);
                            final Form cf = cloudflareFormt;
                            final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, ibr) {
                                @Override
                                public String getSiteKey() {
                                    return getSiteKey(cf.getHtmlCode());
                                }

                                @Override
                                public String getSecureToken() {
                                    return getSecureToken(cf.getHtmlCode());
                                }
                            }.getToken();
                            cloudflareFormt.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                        } finally {
                            antiDDosCaptcha.decrementAndGet();
                        }
                    } else {
                        // recapthca v1
                        if (cloudflareFormt.hasInputFieldByName("recaptcha_response_field")) {
                            // they seem to add multiple input fields which is most likely meant to be corrected by js ?
                            // we will manually remove all those
                            while (cloudflareFormt.hasInputFieldByName("recaptcha_response_field")) {
                                cloudflareFormt.remove("recaptcha_response_field");
                            }
                            while (cloudflareFormt.hasInputFieldByName("recaptcha_challenge_field")) {
                                cloudflareFormt.remove("recaptcha_challenge_field");
                            }
                            // this one is null, needs to be ""
                            if (cloudflareFormt.hasInputFieldByName("message")) {
                                cloudflareFormt.remove("message");
                                cloudflareFormt.put("messsage", "\"\"");
                            }
                            // recaptcha bullshit,
                            String apiKey = cloudflareFormt.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                            if (apiKey == null) {
                                apiKey = ibr.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                                if (apiKey == null) {
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                                }
                            }
                            final DownloadLink dllink = new DownloadLink(null, (this.getDownloadLink() != null ? this.getDownloadLink().getName() + " :: " : "") + "antiDDoS Provider 'Clouldflare' requires Captcha", this.getHost(), "http://" + this.getHost(), true);
                            final Recaptcha rc = new Recaptcha(ibr, this);
                            rc.setId(apiKey);
                            rc.load();
                            final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                            final String response = getCaptchaCode("recaptcha", cf, dllink);
                            if (StringUtils.isEmpty(response)) {
                                throw new PluginException(LinkStatus.ERROR_CAPTCHA, "CloudFlare, invalid captcha response!");
                            }
                            cloudflareFormt.put("recaptcha_challenge_field", rc.getChallenge());
                            cloudflareFormt.put("recaptcha_response_field", Encoding.urlEncode(response));
                        }
                    }
                    final Request originalRequest = ibr.getRequest();
                    ibr.submitForm(cloudflareFormt);
                    if (getCloudflareChallengeForm(ibr) != null) {
                        logger.warning("Wrong captcha");
                        a_captchaRequirement = true;
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA, "CloudFlare, incorrect captcha response!");
                    }
                    // on success cf_clearance cookie is set and a redirect will be present!
                    // we have a problem here when site expects POST request and redirects are always are GETS
                    if (originalRequest instanceof PostRequest) {
                        try {
                            sendRequest(ibr, originalRequest.cloneRequest());
                        } catch (final Exception t) {
                            // we want to preserve proper exceptions!
                            if (t instanceof PluginException) {
                                throw t;
                            }
                            t.printStackTrace();
                            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unexpected CloudFlare related issue", 5 * 60 * 1000l);
                        }
                        // because next round could be 200 response code, you need to nullify this value here.
                        a_captchaRequirement = false;
                        // new sendRequest saves cookie session
                        return;
                    } else if (!ibr.isFollowingRedirects() && ibr.getRedirectLocation() != null) {
                        ibr.getPage(ibr.getRedirectLocation());
                    }
                    a_captchaRequirement = false;
                } else if (ibr.getHttpConnection().getResponseCode() == 403 && ibr.containsHTML("<p>The owner of this website \\([^\\)]*" + Pattern.quote(ibr.getHost()) + "\\) has banned your IP address") && ibr.containsHTML("<title>Access denied \\| [^<]*" + Pattern.quote(ibr.getHost()) + " used CloudFlare to restrict access</title>")) {
                    // website address could be www. or what ever prefixes, need to make sure
                    // eg. within 403 response code, Link; 5544562095341.log; 162684; jdlog://5544562095341
                    // <p>The owner of this website (www.premiumax.net) has banned your IP address (x.x.x.x).</p>
                    // also common when proxies are used?? see keep2share.cc jdlog://5562413173041
                    String ip = ibr.getRegex("your IP address \\((.*?)\\)\\.</p>").getMatch(0);
                    String message = ibr.getHost() + " has banned your IP Address" + (StringUtils.isEmpty(ip) ? "!" : "! " + ip);
                    logger.warning(message);
                    throw new PluginException(LinkStatus.ERROR_FATAL, message);
                } else if (responseCode == 503 && cloudflareFormt != null) {
                    // 503 response code with javascript math section
                    final String[] line1 = ibr.getRegex("var (?:t,r,a,f,|s,t,o,[a-z,]+) (\\w+)=\\{\"(\\w+)\":([^\\}]+)").getRow(0);
                    String line2 = ibr.getRegex("(\\;" + line1[0] + "." + line1[1] + ".*?t\\.length\\;)").getMatch(0);
                    StringBuilder sb = new StringBuilder();
                    sb.append("var a={};\r\nvar t=\"" + Browser.getHost(ibr.getURL(), true) + "\";\r\n");
                    sb.append("var " + line1[0] + "={\"" + line1[1] + "\":" + line1[2] + "}\r\n");
                    sb.append(line2);
                    ScriptEngineManager mgr = JavaScriptEngineFactory.getScriptEngineManager(this);
                    ScriptEngine engine = mgr.getEngineByName("JavaScript");
                    long answer = ((Number) engine.eval(sb.toString())).longValue();
                    cloudflareFormt.getInputFieldByName("jschl_answer").setValue(answer + "");
                    Thread.sleep(5500);
                    ibr.submitForm(cloudflareFormt);
                    // if it works, there should be a redirect.
                    if (!ibr.isFollowingRedirects() && ibr.getRedirectLocation() != null) {
                        ibr.getPage(ibr.getRedirectLocation());
                    }
                } else if (responseCode == 521) {
                    // this basically indicates that the site is down, no need to retry.
                    a_responseCode5xx++;
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "CloudFlare says \"Origin Sever\" is down!", 5 * 60 * 1000l);
                } else if (responseCode == 504 || responseCode == 520 || responseCode == 522 || responseCode == 523 || responseCode == 525) {
                    // these warrant retry instantly, as it could be just slave issue? most hosts have 2 DNS response to load balance.
                    // additional request could work via additional IP
                    /**
                     * @see clouldflare_504_snippet.html
                     */
                    // HTTP/1.1 504 Gateway Time-out
                    // HTTP/1.1 520 Origin Error
                    // HTTP/1.1 522 Origin Connection Time-out
                    /**
                     * @see cloudflare_523_snippet.html
                     */
                    // HTTP/1.1 523 Origin Unreachable
                    // HTTP/1.1 525 Origin SSL Handshake Error || >CloudFlare is unable to establish an SSL connection to the origin
                    // server.<
                    // cache system with possible origin dependency... we will wait and retry
                    if (a_responseCode5xx == 4) {
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "CloudFlare can not contact \"Origin Server\"", 5 * 60 * 1000l);
                    }
                    a_responseCode5xx++;
                    // this html based cookie, set by <meta (for responseCode 522)
                    // <meta http-equiv="set-cookie" content="cf_use_ob=0; expires=Sat, 14-Jun-14 14:35:38 GMT; path=/">
                    String[] metaCookies = ibr.getRegex("<meta http-equiv=\"set-cookie\" content=\"(.*?; expries=.*?; path=.*?\";?(?: domain=.*?;?)?)\"").getColumn(0);
                    if (metaCookies != null && metaCookies.length != 0) {
                        final List<String> cookieHeaders = Arrays.asList(metaCookies);
                        final String date = ibr.getHeaders().get("Date");
                        final String host = Browser.getHost(ibr.getURL());
                        // get current cookies
                        final Cookies ckies = ibr.getCookies(host);
                        // add meta cookies to current previous request cookies
                        for (int i = 0; i < cookieHeaders.size(); i++) {
                            final String header = cookieHeaders.get(i);
                            ckies.add(Cookies.parseCookies(header, host, date));
                        }
                        // set ckies as current cookies
                        ibr.getHttpConnection().getRequest().setCookies(ckies);
                    }
                    Thread.sleep(2500);
                    // effectively refresh page!
                    try {
                        sendRequest(ibr, ibr.getRequest().cloneRequest());
                    } catch (final Exception t) {
                        // we want to preserve proper exceptions!
                        if (t instanceof PluginException) {
                            throw t;
                        }
                        t.printStackTrace();
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unexpected CloudFlare related issue", 5 * 60 * 1000l);
                    }
                    // new sendRequest saves cookie session
                    return;
                } else if (responseCode == 429 && ibr.containsHTML("<title>Too Many Requests</title>|<title>Error 429 - To many requests</title>")) {
                    if (a_responseCode429 == 4) {
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE);
                    }
                    a_responseCode429++;
                    // been blocked! need to wait 1min before next request. (says k2sadmin, each site could be configured differently)
                    Thread.sleep(61000);
                    // try again! -NOTE: this isn't stable compliant-
                    try {
                        sendRequest(ibr, ibr.getRequest().cloneRequest());
                    } catch (final Exception t) {
                        // we want to preserve proper exceptions!
                        if (t instanceof PluginException) {
                            throw t;
                        }
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE);
                    }
                    // new sendRequest saves cookie session
                    return;
                    // new code here...
                    // <script type="text/javascript">
                    // //<![CDATA[
                    // try{if (!window.CloudFlare) {var
                    // CloudFlare=[{verbose:0,p:1408958160,byc:0,owlid:"cf",bag2:1,mirage2:0,oracle:0,paths:{cloudflare:"/cdn-cgi/nexp/dokv=88e434a982/"},atok:"661da6801927b0eeec95f9f3e160b03a",petok:"107d6db055b8700cf1e7eec1324dbb7be6b978d0-1408974417-1800",zone:"fileboom.me",rocket:"0",apps:{}}];CloudFlare.push({"apps":{"ape":"3a15e211d076b73aac068065e559c1e4"}});!function(a,b){a=document.createElement("script"),b=document.getElementsByTagName("script")[0],a.async=!0,a.src="//ajax.cloudflare.com/cdn-cgi/nexp/dokv=97fb4d042e/cloudflare.min.js",b.parentNode.insertBefore(a,b)}()}}catch(e){};
                    // //]]>
                    // </script>
                } else if (responseCode == 200 && ibr.containsHTML("<title>Suspected phishing site\\s*\\|\\s*CloudFlare</title>")) {
                    final Form phishing = ibr.getFormbyAction("/cdn-cgi/phish-bypass");
                    if (phishing == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    ibr.submitForm(phishing);
                } else {
                    // nothing wrong, or something wrong (unsupported format)....
                    // commenting out return prevents caching of cookies per request
                    // return;
                }
                // get cookies we want/need.
                // refresh these with every getPage/postPage/submitForm?
                final Cookies add = ibr.getCookies(ibr.getHost());
                for (final Cookie c : add.getCookies()) {
                    if (new Regex(c.getKey(), "(__cfduid|cf_clearance)").matches()) {
                        cookies.put(c.getKey(), c.getValue());
                    }
                }
            }
            // save the session!
            synchronized (antiDDoSCookies) {
                // why do I clear cookies? -raztok20160304
                // antiDDoSCookies.clear();
                antiDDoSCookies.putAll(cookies);
            }
        }
    }

    private Form getCloudflareChallengeForm(final Browser ibr) {
        // speed things up, maintain our own code vs using br.getformby each time has to search and construct forms/inputfields! this is
        // slow!
        final Form[] forms = ibr.getForms();
        for (final Form form : forms) {
            if (form.getStringProperty("id") != null && (form.getStringProperty("id").equalsIgnoreCase("challenge-form") || form.getStringProperty("id").equalsIgnoreCase("ChallengeForm"))) {
                return form;
            }
        }
        return null;
    }

    /**
     *
     * @author raztoki
     */
    @SuppressWarnings("unused")
    private boolean requestHeadersHasKeyNValueStartsWith(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        if (ibr.getHttpConnection().getHeaderField(k) != null && ibr.getHttpConnection().getHeaderField(k).toLowerCase(Locale.ENGLISH).startsWith(v.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    /**
     *
     * @author raztoki
     */
    private boolean requestHeadersHasKeyNValueContains(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        if (ibr.getHttpConnection().getHeaderField(k) != null && ibr.getHttpConnection().getHeaderField(k).toLowerCase(Locale.ENGLISH).contains(v.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    private static final String cfRequiredCookies = "__cfduid|cf_clearance";

    /**
     * returns true if browser contains cookies that match expected
     *
     * @author raztoki
     * @param ibr
     * @return
     */
    protected boolean containsCloudflareCookies(final Browser ibr) {
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), cfRequiredCookies).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Class<? extends Keep2shareConfig> getConfigInterface() {
        return Keep2shareConfig.class;
    }
}
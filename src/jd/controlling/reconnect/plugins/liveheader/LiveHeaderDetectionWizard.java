package jd.controlling.reconnect.plugins.liveheader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import jd.controlling.FavIconController;
import jd.controlling.reconnect.ReconnectConfig;
import jd.controlling.reconnect.ReconnectException;
import jd.controlling.reconnect.ReconnectPluginController;
import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.RouterUtils;
import jd.controlling.reconnect.ipcheck.IP;
import jd.controlling.reconnect.plugins.liveheader.remotecall.RecollInterface;
import jd.controlling.reconnect.plugins.liveheader.remotecall.RouterData;
import jd.controlling.reconnect.plugins.liveheader.translate.T;
import jd.controlling.reconnect.plugins.upnp.UPNPRouterPlugin;
import jd.controlling.reconnect.plugins.upnp.UpnpRouterDevice;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.utils.JDUtilities;

import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.Hash;
import org.appwork.utils.Regex;
import org.appwork.utils.event.ProcessCallBack;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.logging.Log;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.remotecall.RemoteClient;

public class LiveHeaderDetectionWizard {
    // "update3.jdownloader.org/recoll";
    // "192.168.2.250/ces";
    private static final String     UPDATE3_JDOWNLOADER_ORG_RECOLL = "update3.jdownloader.org/recoll";

    private String                  mac;

    private String                  manufactor;

    private InetAddress             gatewayAdress;

    private String                  username;

    private String                  password;

    private String                  firmware;

    private String                  sslResponse;

    private String                  sslTitle;

    private int                     sslPTagsCount;

    private int                     sslFrameTagCount;

    private String                  sslFavIconHash;

    private String                  sslException;

    private int                     sslResponseCode;

    private HashMap<String, String> sslResponseHeaders;

    private String                  response;

    private String                  title;

    private int                     pTagsCount;

    private int                     frameTagCount;

    private String                  favIconHash;

    private String                  exception;

    private int                     responseCode;

    private HashMap<String, String> responseHeaders;

    private RecollInterface         recoll;

    public LiveHeaderDetectionWizard() {

        recoll = new RemoteClient(LiveHeaderDetectionWizard.UPDATE3_JDOWNLOADER_ORG_RECOLL).getFactory().newInstance(RecollInterface.class);
    }

    private LiveHeaderReconnect getPlugin() {
        return (LiveHeaderReconnect) ReconnectPluginController.getInstance().getPluginByID(LiveHeaderReconnect.ID);
    }

    // public int runOfflineScan() throws InterruptedException {
    // int ret = -1;
    // // final ArrayList<String[]> scripts =
    // // LiveHeaderReconnect.getLHScripts();
    //
    // this.txtName = new JTextField();
    // this.txtManufactor = new JTextField();
    // this.txtUser = new JTextField();
    // this.txtPass = new JTextField();
    // this.txtIP = new JTextField();
    //
    // final JPanel p = new JPanel(new MigLayout("ins 5,wrap 2",
    // "[][grow,fill]", "[grow,fill]"));
    // p.add(new
    // JLabel("Please enter your router information as far as possible."),
    // "spanx");
    // p.add(new JLabel("Model Name"));
    // p.add(this.txtName);
    //
    // p.add(new JLabel("Manufactor"));
    // p.add(this.txtManufactor);
    //
    // // p.add(new JLabel("Firmware"));
    // // p.add(this.txtFirmware);
    //
    // p.add(new JLabel("Webinterface IP"));
    // p.add(this.txtIP);
    //
    // p.add(new JLabel("Webinterface User"));
    // p.add(this.txtUser);
    // p.add(new JLabel("Webinterface Password"));
    // p.add(this.txtPass);
    // this.txtUser.setText(this.getPlugin().getUser());
    // this.txtPass.setText(this.getPlugin().getPassword());
    // this.txtName.setText(this.getPlugin().getRouterName());
    // this.txtIP.setText(this.getPlugin().getRouterIP());
    // while (true) {
    //
    // final ContainerDialog routerInfo = new ContainerDialog(0,
    // "Enter Router Information", p, null, "Continue", null);
    //
    // try {
    // Dialog.getInstance().showDialog(routerInfo);
    // } catch (DialogNoAnswerException e) {
    // return -1;
    // }
    // try {
    // if (this.txtUser.getText().trim().length() < 2 ||
    // this.txtPass.getText().trim().length() < 2) {
    //
    // Dialog.getInstance().showConfirmDialog(0, "Warning",
    // "Username and Password are not set. In most cases, \r\nthese information is required for a successful reconnection.\r\n\r\nContinue anyway?");
    //
    // }
    //
    // if (!RouterUtils.checkPort(this.txtIP.getText().trim())) {
    // Dialog.getInstance().showConfirmDialog(0, "Warning",
    // "There is no Webinterface at http://" + this.txtIP.getText() +
    // "\r\nAre you sure that the Router IP is correct?\r\nA correct Router IP is required to find the correct settings.\r\n\r\nContinue anyway?");
    // }
    // String man = this.txtManufactor.getText().trim();
    // String name = this.txtName.getText().trim();
    // ArrayList<RouterData> tests = this.scanOfflineRouters(name.length() > 0 ?
    // ".*" + name.toLowerCase().replaceAll("\\W", ".*?") + ".*" : null,
    // man.toLowerCase().length() > 0 ? ".*" +
    // man.toLowerCase().replaceAll("\\W", ".*?") + ".*" : null);
    // ret = runTests(tests);
    // if (ret <= 0) {
    // try {
    // Dialog.getInstance().showConfirmDialog(0, "Warning",
    // "Could not find correct settings based on your inputs?\r\n\r\nTry again?");
    // continue;
    // } catch (DialogClosedException e) {
    // return -1;
    // } catch (DialogCanceledException e) {
    // return -1;
    // }
    // } else {
    // return ret;
    // }
    // } catch (DialogClosedException e) {
    // return -1;
    // } catch (DialogCanceledException e) {
    // continue;
    // }
    //
    // }
    //
    // }

    private ArrayList<ReconnectResult> runTests(ArrayList<RouterData> tests, ProcessCallBack processCallBack) throws InterruptedException, DialogClosedException, DialogCanceledException {

        boolean pre = JsonConfig.create(ReconnectConfig.class).isIPCheckGloballyDisabled();
        try {
            if (pre) {

                Dialog.getInstance().showConfirmDialog(0, _GUI._.literally_warning(), T._.ipcheck());
                JsonConfig.create(ReconnectConfig.class).setIPCheckGloballyDisabled(false);

            }

            ArrayList<RouterData> bestMatches = filterBestMatches(tests);
            ArrayList<ReconnectResult> ret = testList(bestMatches, processCallBack);
            if (ret != null && ret.size() > 0 && JsonConfig.create(LiveHeaderReconnectSettings.class).isAutoSearchBestMatchFilterEnabled()) { return ret; }
            // test all if best matches did not succeed
            ret = testList(tests, processCallBack);

            return ret;
        } finally {

            JsonConfig.create(ReconnectConfig.class).setIPCheckGloballyDisabled(pre);
            if (pre) {
                Dialog.getInstance().showMessageDialog(T._.ipcheckreverted());
            }
        }

    }

    private ArrayList<RouterData> filterBestMatches(ArrayList<RouterData> tests) {
        int total = 0;
        for (Iterator<RouterData> it = tests.iterator(); it.hasNext();) {
            total += it.next().getPriorityIndicator();
        }
        float avg = total / (float) tests.size();
        ArrayList<RouterData> ret = new ArrayList<RouterData>();
        for (Iterator<RouterData> it = tests.iterator(); it.hasNext();) {
            RouterData next = it.next();

            if (isTopMatch(next, avg)) {
                ret.add(next);
                it.remove();
            }
        }
        return ret;
    }

    private String getRegex(final String routerName) {
        if (routerName == null || routerName.trim().length() == 0) { return null; }
        final String ret = routerName.replaceAll("[^a-zA-Z0-9]+", ".*");

        // add wildcard between number and non-numbers
        final StringBuilder r = new StringBuilder();
        r.append(ret.charAt(0));
        for (int i = 1; i < ret.length(); i++) {
            final char c = ret.charAt(i - 1);
            final char c1 = ret.charAt(i);

            if (Character.isDigit(c) && !Character.isDigit(c1) || !Character.isDigit(c) && Character.isDigit(c1)) {
                r.append(".*");
            }
            r.append(c1);

        }
        return r.toString();
    }

    private boolean isTopMatch(RouterData rd, float avg) {
        // many matches with this script
        if (rd.getPriorityIndicator() > avg * 2) return true;

        if (!matches(firmware, rd.getFirmware())) return false;
        if (!matches(routerName, rd.getRouterName())) return false;
        if (!matches(manufactor, rd.getManufactor())) return false;

        return true;
    }

    private boolean matches(String userInput, String database) {
        String regex = getRegex(userInput);

        if (regex != null) {
            //
            if (database == null) return false;
            String m = new Regex(userInput, regex).getMatch(-1);
            return database.equals(m);
        }
        // no userinput for this value
        return true;
    }

    public ArrayList<ReconnectResult> testList(ArrayList<RouterData> tests, ProcessCallBack processCallBack) throws InterruptedException, DialogClosedException, DialogCanceledException {

        if (username.trim().length() < 2 || password.trim().length() < 2) {
            for (int i = 0; i < tests.size(); i++) {
                String sc = tests.get(i).getScript().toLowerCase(Locale.ENGLISH);
                if (sc.contains("%%%username%%%") || sc.contains("%%%user%%%") || sc.contains("%%%pass%%%") || sc.contains("%%%password%%%")) {
                    // TODO
                    DataCompareDialog dcd = new DataCompareDialog(gatewayAdressHost, firmware, manufactor, routerName, JsonConfig.create(LiveHeaderReconnectSettings.class).getUserName(), JsonConfig.create(LiveHeaderReconnectSettings.class).getPassword());
                    dcd.setLoginsOnly(true);
                    Dialog.getInstance().showDialog(dcd);

                    username = dcd.getUsername();
                    password = dcd.getPassword();
                    break;
                }
            }
        }
        ArrayList<ReconnectResult> ret = new ArrayList<ReconnectResult>();

        for (int i = 0; i < tests.size(); i++) {

            RouterData test = tests.get(i);
            processCallBack.setStatusString(getPlugin(), T._.jd_controlling_reconnect_plugins_liveheader_LiveHeaderDetectionWizard_runTests((i + 1), tests.size(), test.getManufactor() + " - " + test.getRouterName()));
            String sc = tests.get(i).getScript().toLowerCase(Locale.ENGLISH);

            if ((username.trim().length() < 2 && (sc.contains("%%%username%%%") || sc.contains("%%%user%%%"))) || (password.trim().length() < 2 && (sc.contains("%%%pass%%%") || sc.contains("%%%password%%%")))) {
                // script requires data which is not available
                processCallBack.setProgress(getPlugin(), Math.min(99, (i + 1) * 100 / tests.size()));
                Thread.sleep(1000);
                continue;
                // System.out.println("WILL FAIL");
            }

            ReconnectResult res;
            try {
                LiveHeaderInvoker inv = new LiveHeaderInvoker(getPlugin(), test.getScript(), username, password, gatewayAdressHost, routerName != null && routerName.trim().length() > 0 ? routerName : test.getRouterName());
                res = inv.validate();
                ((LiveHeaderReconnectResult) res).setRouterData(test);
                if (res != null && res.isSuccess()) {

                    ret.add(res);
                    processCallBack.setStatus(this, ret);
                    if (i < tests.size() - 1) {

                        if (ret.size() == 1) Dialog.getInstance().showConfirmDialog(0, T._.LiveHeaderDetectionWizard_testList_firstSuccess_title(), T._.LiveHeaderDetectionWizard_testList_firstsuccess_msg(TimeFormatter.formatMilliSeconds(res.getSuccessDuration(), 0)), NewTheme.I().getIcon("ok", 32), T._.LiveHeaderDetectionWizard_testList_ok(), T._.LiveHeaderDetectionWizard_testList_use());
                        return ret;
                    }
                }
            } catch (DialogNoAnswerException e) {
                break;
            } catch (ReconnectException e) {
                Log.exception(e);
            }
            processCallBack.setProgress(getPlugin(), Math.min(99, (i + 1) * 100 / tests.size()));
        }
        // JsonConfig.create(ReconnectConfig.class).setGlobalFailedCounter(0);
        // JsonConfig.create(ReconnectConfig.class).setFailedCounter(0);
        // JsonConfig.create(ReconnectConfig.class).setGlobalSuccessCounter(0);
        // JsonConfig.create(ReconnectConfig.class).setSuccessCounter(0);
        // if (fastestScript != null) {
        // JsonConfig.create(LiveHeaderReconnectSettings.class).setRouterIP(fastestScript.getRouterIP());
        // JsonConfig.create(LiveHeaderReconnectSettings.class).setRouterData(fastestScript);
        // JsonConfig.create(LiveHeaderReconnectSettings.class).setUserName(fastestScript.getUsername());
        // JsonConfig.create(LiveHeaderReconnectSettings.class).setPassword(fastestScript.getPassword());
        // JsonConfig.create(LiveHeaderReconnectSettings.class).setScript(fastestScript.getScript());
        // JsonConfig.create(ReconnectConfig.class).setSecondsBeforeFirstIPCheck((int)
        // Math.min(5, fastestScript.getOfflineDuration() + 1000));
        // JsonConfig.create(ReconnectConfig.class).setSecondsToWaitForIPChange(Math.max(fastestScript.getTestDuration()
        // * 4, 30));
        // processCallBack.setProgress(99);
        // processCallBack.showMessage(T._.autodetection_success(TimeFormatter.formatMilliSeconds(fastestScript.getTestDuration(),
        // 0)));
        // return fastestTime;
        // }
        return ret;
    }

    public ArrayList<ReconnectResult> runOnlineScan(ProcessCallBack processCallBack) throws InterruptedException, UnknownHostException {
        try {

            recoll.isAlive();

            processCallBack.setStatusString(getPlugin(), T._.LiveHeaderDetectionWizard_runOnlineScan_collect());
            collectInfo();
            specialCollectInfo();
            while (true) {
                userConfirm();
                try {

                    if (!IP.isValidRouterIP(gatewayAdressIP)) {

                        Dialog.getInstance().showConfirmDialog(0, _GUI._.literally_warning(), T._.LiveHeaderDetectionWizard_runOnlineScan_warning_badip(gatewayAdressHost), NewTheme.I().getIcon("warning", 32), _GUI._.literally_yes(), _GUI._.literally_edit());

                    }
                    break;
                } catch (DialogCanceledException e) {
                    continue;
                }
            }
            scanRemoteInfo();
            specials();
            ArrayList<RouterData> list = downloadRouterDatasByAutoDetectValues();

            return runTests(list, processCallBack);
        } catch (DialogNoAnswerException e) {
            throw new InterruptedException();
        } catch (InterruptedException e) {
            throw e;
        }

    }

    private void specialCollectInfo() throws InterruptedException {

        // speedports
        if ("speedport.ip".equalsIgnoreCase(gatewayAdressHost)) {

            try {
                String status = new Browser().getPage("http://speedport.ip/top_status.stm");
                String fw = new Regex(status, "runtime_code_version=\"(.*?)\"").getMatch(0);
                if (fw != null) firmware = fw;
                String name = new Regex(status, "var prodname=\"(.*?)\"").getMatch(0);
                if (name != null) {
                    routerName = name.replace("_", " ");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            // speedports

            // extract name from sourc

            try {
                String status = new Browser().getPage("https://speedport.ip/top_status.stm");
                String name = new Regex(status, "var prodname=\"(.*?)\"").getMatch(0);
                if (name != null) {
                    routerName = name.replace("_", " ");
                }
                String fw = new Regex(status, "runtime_code_version=\"(.*?)\"").getMatch(0);
                if (fw != null) firmware = fw;
            } catch (Exception e) {

            }

        }
    }

    private void specials() {

    }

    private String trim(final String stringToTrim) {

        return stringToTrim == null ? null : stringToTrim.trim();
    }

    private void scanRemoteInfo() {

        final Browser br = new Browser();
        try {
            this.sslResponse = br.getPage("https://" + this.gatewayAdressHost);

            this.sslTitle = br.getRegex("<title>(.*?)</title>").getMatch(0);
            this.sslPTagsCount = br.toString().split("<p>").length;
            this.sslFrameTagCount = br.toString().split("<frame").length;
            // get favicon and build hash
            try {
                final BufferedImage image = FavIconController.getInstance().downloadFavIcon(this.gatewayAdressHost);
                final File imageFile = JDUtilities.getResourceFile("tmp/routerfav.png", true);
                imageFile.delete();
                imageFile.deleteOnExit();
                ImageIO.write(image, "png", imageFile);
                this.sslFavIconHash = Hash.getMD5(imageFile);
            } catch (final Exception e) {

            }

        } catch (final Throwable e) {

            this.sslException = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        URLConnectionAdapter con = br.getHttpConnection();
        if (con != null) {
            this.sslResponseCode = con.getResponseCode();
            this.sslResponseHeaders = new HashMap<String, String>();

            for (final Entry<String, List<String>> next : con.getHeaderFields().entrySet()) {
                for (final String value : next.getValue()) {
                    this.sslResponseHeaders.put(next.getKey().toLowerCase(), value);
                }
            }
        }

        try {
            this.response = br.getPage("http://" + this.gatewayAdressHost);

            this.title = br.getRegex("<title>(.*?)</title>").getMatch(0);
            this.pTagsCount = br.toString().split("<p>").length;
            this.frameTagCount = br.toString().split("<frame").length;
            // get favicon and build hash
            try {
                final BufferedImage image = FavIconController.getInstance().downloadFavIcon(this.gatewayAdressHost);
                final File imageFile = JDUtilities.getResourceFile("tmp/routerfav.png", true);
                imageFile.delete();
                imageFile.deleteOnExit();
                ImageIO.write(image, "png", imageFile);
                this.favIconHash = Hash.getMD5(imageFile);
            } catch (final Exception e) {

            }
        } catch (final IOException e) {
            this.exception = e.getClass().getSimpleName() + ": " + e.getMessage();

        }

        con = br.getHttpConnection();
        if (con != null) {
            this.responseCode = con.getResponseCode();
            this.responseHeaders = new HashMap<String, String>();
            for (final Entry<String, List<String>> next : con.getHeaderFields().entrySet()) {
                for (final String value : next.getValue()) {
                    this.responseHeaders.put(next.getKey().toLowerCase(), value);
                }
            }
        }
    }

    private ArrayList<RouterData> downloadRouterDatasByAutoDetectValues() {

        RouterData rd = getRouterData();
        // debug
        rd.setMac(null);
        ArrayList<RouterData> scripts = recoll.findRouter(rd);

        final ArrayList<RouterData> unique = toUnique(scripts);

        return unique;

    }

    public static ArrayList<RouterData> toUnique(ArrayList<RouterData> scripts) {
        final HashMap<String, RouterData> helper = new HashMap<String, RouterData>();
        final ArrayList<RouterData> unique = new ArrayList<RouterData>(scripts.size());
        for (final RouterData e : scripts) {
            if (e.getScript() != null) {
                String script = prepScript(e.getScript());
                System.out.println("____________________________________________________________");
                System.out.println(Hash.getMD5(script) + " : " + script + " " + script.toCharArray().length);
                if (!helper.containsKey(script)) {
                    System.out.println("ADD");
                    helper.put(script, e);
                    unique.add(e);
                    e.setPriorityIndicator(e.getSuccess() - e.getFailed() + e.getCommitDupe());
                } else {
                    System.out.println("DUPE");
                    final RouterData m = helper.get(script);
                    m.setPriorityIndicator(m.getPriorityIndicator() + 1 + e.getSuccess() - e.getFailed() + e.getCommitDupe());

                }
            }
        }
        return unique;
    }

    private static String prepScript(String script) {
        script = script.toUpperCase(Locale.ENGLISH).replaceAll("\\s+", "");
        // script = Pattern.compile("\\[\\[\\[(/?)hsrc\\]\\]\\]",
        // Pattern.CASE_INSENSITIVE).matcher(script).replaceAll("[[[$1HSRC]]]");
        // script = Pattern.compile("\\[\\[\\[(/?)request",
        // Pattern.CASE_INSENSITIVE).matcher(script).replaceAll("[[[$1REQUEST");
        // script = Pattern.compile("\\[\\[\\[(/?)step",
        // Pattern.CASE_INSENSITIVE).matcher(script).replaceAll("[[[$1STEP");
        // script = Pattern.compile("\\[\\[\\[(/?)response",
        // Pattern.CASE_INSENSITIVE).matcher(script).replaceAll("[[[$1RESPONSE");
        // script = Pattern.compile("\\[\\[\\[(/?)parse",
        // Pattern.CASE_INSENSITIVE).matcher(script).replaceAll("[[[$1PARSE");

        return script;
    }

    private RouterData getRouterData() {
        RouterData search = new RouterData();
        search.setException(exception);
        search.setFavIconHash(favIconHash);
        search.setFirmware(firmware);
        search.setFrameTagCount(frameTagCount);
        search.setMac(mac);
        search.setManufactor(manufactor);
        search.setpTagsCount(pTagsCount);
        search.setResponse(response);
        search.setResponseCode(responseCode);
        search.setResponseHeaders(responseHeaders);
        search.setRouterIP(gatewayAdressIP);
        search.setRouterHost(gatewayAdressHost);
        search.setRouterName(routerName);
        search.setSslException(sslException);
        search.setSslFavIconHash(sslFavIconHash);
        search.setSslFrameTagCount(sslFrameTagCount);
        search.setSslPTagsCount(sslPTagsCount);
        search.setSslResponse(sslResponse);
        search.setSslResponseCode(sslResponseCode);
        search.setSslResponseHeaders(sslResponseHeaders);
        search.setSslTitle(sslTitle);
        search.setTitle(title);
        return search;
    }

    private void userConfirm() throws DialogClosedException, DialogCanceledException {
        while (true) {
            System.out.println("UserConfirm");
            DataCompareDialog dcd = new DataCompareDialog(gatewayAdressHost, firmware, manufactor, routerName, JsonConfig.create(LiveHeaderReconnectSettings.class).getUserName(), JsonConfig.create(LiveHeaderReconnectSettings.class).getPassword());
            Dialog.getInstance().showDialog(dcd);
            username = dcd.getUsername();

            password = dcd.getPassword();
            manufactor = dcd.getManufactor();
            routerName = dcd.getRouterName();
            firmware = dcd.getFirmware();
            try {
                gatewayAdress = InetAddress.getByName(dcd.getHostName());
                gatewayAdressIP = gatewayAdress.getHostAddress();
                gatewayAdressHost = gatewayAdress.getHostName();
                break;
            } catch (IOException e) {
                Dialog.getInstance().showConfirmDialog(0, _GUI._.literall_error(), T._.LiveHeaderDetectionWizard_runOnlineScan_warning_badhost(dcd.getHostName()), NewTheme.I().getIcon("error", 32), _GUI._.literally_edit(), null);

            }
        }
    }

    private UpnpRouterDevice myUpnpDevice = null;

    private String           routerName;

    private String           gatewayAdressHost;

    private String           gatewayAdressIP;

    private void collectInfo() throws UnknownHostException, InterruptedException {
        final UPNPRouterPlugin upnp = (UPNPRouterPlugin) ReconnectPluginController.getInstance().getPluginByID(UPNPRouterPlugin.ID);

        ArrayList<UpnpRouterDevice> devices = upnp.getDevices();

        String gatewayIP = JsonConfig.create(LiveHeaderReconnectSettings.class).getRouterIP();

        if (!IP.isValidRouterIP(gatewayIP)) {
            final InetAddress ia = RouterUtils.getAddress(true);
            if (ia != null && IP.isValidRouterIP(ia.getHostAddress())) {
                gatewayIP = ia.getHostName();
            }
        }
        mac = null;
        manufactor = null;
        gatewayAdress = InetAddress.getByName(gatewayIP);
        gatewayAdressHost = gatewayAdress.getHostName();
        gatewayAdressIP = gatewayAdress.getHostAddress();
        try {
            mac = RouterUtils.getMacAddress(gatewayAdress).replace(":", "").toUpperCase(Locale.ENGLISH);

            manufactor = RouterSender.getManufactor(mac);
        } catch (final InterruptedException e) {
            throw e;
        } catch (final Exception e) {
            e.printStackTrace();
        }
        myUpnpDevice = null;
        for (final UpnpRouterDevice d : devices) {
            if (d.getHost() != null) {
                try {
                    if (gatewayAdress.equals(InetAddress.getByName(d.getHost()))) {
                        myUpnpDevice = d;
                        break;
                    }
                } catch (final UnknownHostException e) {
                    // nothing
                }
            }
        }
        // Use upnp manufactor if given
        if (myUpnpDevice != null && myUpnpDevice.getManufactor() != null) {
            manufactor = myUpnpDevice.getManufactor();
        }
        try {
            routerName = JsonConfig.create(LiveHeaderReconnectSettings.class).getRouterData().getRouterName();
        } catch (Exception e) {
        }
        if (routerName == null || routerName.trim().length() == 0 || "unknown".equalsIgnoreCase(routerName)) {
            // try to convert domain to routername
            if (!gatewayAdressHost.equals(gatewayAdressIP)) {
                routerName = gatewayAdressHost;
                int i = routerName.lastIndexOf(".");
                if (i > 0) routerName = routerName.substring(0, i);
            }
        }
        if (myUpnpDevice != null) {
            if (myUpnpDevice.getModelname() != null) {
                routerName = myUpnpDevice.getModelname();
            } else if (myUpnpDevice.getFriendlyname() != null) {
                routerName = myUpnpDevice.getFriendlyname();
            }

        }
    }

}
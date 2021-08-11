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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtPasswordField;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.jdownloader.gui.InputChangedCallbackInterface;
import org.jdownloader.plugins.accounts.AccountBuilderInterface;
import org.jdownloader.plugins.components.usenet.UsenetServer;

import jd.PluginWrapper;
import jd.gui.swing.components.linkbutton.JLink;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 4, names = { "simply-premium.com" }, urls = { "" })
public class SimplyPremiumCom2 extends HighWayCore {
    public SimplyPremiumCom2(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.simply-premium.com/vip");
    }

    @Override
    public String getAGBLink() {
        return "https://www.simply-premium.com/terms";
    }

    @Override
    protected String getAPIBase() {
        /* Special: Requires "www." */
        return "https://www." + this.getHost() + "/apiV2.php";
    }

    @Override
    protected String getWebsiteBase() {
        /* Special: Requires "www." */
        return "https://www." + this.getHost() + "/";
    }

    @Override
    protected boolean useApikeyLogin() {
        return true;
    }

    @Override
    public List<UsenetServer> getAvailableUsenetServer() {
        final List<UsenetServer> ret = new ArrayList<UsenetServer>();
        ret.addAll(UsenetServer.createServerList("reader.simply-premium.com", false, 119));
        ret.addAll(UsenetServer.createServerList("reader.simply-premium.com", true, 563));
        return ret;
    }

    @Override
    protected void exceptionAccountInvalid(final Account account) throws PluginException {
        showAPILoginInformation();
        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAPI Key!\r\nDeinen API Key findest du hier: simply-premium.com/profile", PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid API Key!\r\nYou can find your API Key here: simply-premium.com/profile", PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
    }

    private Thread showAPILoginInformation() {
        final Thread thread = new Thread() {
            public void run() {
                final String apiCredsURLWithoutProtocol = "simply-premium.com/profile";
                try {
                    String message = "";
                    final String title;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        title = "simply-premium.com - Login";
                        message += "Hallo liebe(r) simply-premium NutzerIn\r\n";
                        message += "Um deinen simply-premium Account in JDownloader verwenden zu können, musst du folgende Schritte beachten:\r\n";
                        message += "1. Öffne diesen Link im Browser falls das nicht automatisch passiert:\r\n\t'" + apiCredsURLWithoutProtocol + "'\t\r\n";
                        message += "2. Logge dich mit dem dort aufgeführten API Key in JDownloader ein.\r\n";
                        message += "Falls du myjdownloader/headless verwendest, gib diesen API Key in das Benutzername UND Passwort Feld ein.";
                    } else {
                        title = "simply-premium - Login";
                        message += "Hello dear simply-premium user\r\n";
                        message += "In order to use this service in JDownloader, you need to follow these steps:\r\n";
                        message += "1. Open this URL in your browser if it is not opened automatically:\r\n\t'" + apiCredsURLWithoutProtocol + "'\t\r\n";
                        message += "2. Enter your API Key to login in JDownloader.\r\n";
                        message += "If you're using myjdownloader/headless, enter your API Key in the username AND password fields.";
                    }
                    final ConfirmDialog dialog = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, title, message);
                    dialog.setTimeout(2 * 60 * 1000);
                    if (CrossSystem.isOpenBrowserSupported() && !Application.isHeadless()) {
                        CrossSystem.openURL("https://" + apiCredsURLWithoutProtocol);
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
    public AccountBuilderInterface getAccountFactory(final InputChangedCallbackInterface callback) {
        return new SimplyPremiumAccountFactory(callback);
    }

    public static class SimplyPremiumAccountFactory extends MigPanel implements AccountBuilderInterface {
        /**
         *
         */
        private static final long serialVersionUID = 1L;
        private final String      PINHELP          = "Enter your API Key";

        private String getPassword() {
            if (this.pass == null) {
                return null;
            }
            if (EMPTYPW.equals(new String(this.pass.getPassword()))) {
                return null;
            }
            return new String(this.pass.getPassword());
        }

        public boolean updateAccount(Account input, Account output) {
            boolean changed = false;
            if (!StringUtils.equals(input.getUser(), output.getUser())) {
                output.setUser(input.getUser());
                changed = true;
            }
            if (!StringUtils.equals(input.getPass(), output.getPass())) {
                output.setPass(input.getPass());
                changed = true;
            }
            return changed;
        }

        private final ExtPasswordField pass;
        private static String          EMPTYPW = "                 ";

        public SimplyPremiumAccountFactory(final InputChangedCallbackInterface callback) {
            super("ins 0, wrap 2", "[][grow,fill]", "");
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                add(new JLabel("Hier findest du deinen API Key:"));
            } else {
                add(new JLabel("Click here to find your API Key:"));
            }
            add(new JLink("https://www.simply-premium.com/profile"));
            add(new JLabel("API Key:"));
            add(this.pass = new ExtPasswordField() {
                @Override
                public void onChanged() {
                    callback.onChangedInput(this);
                }
            }, "");
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                pass.setHelpText("Gib deinen API Key ein");
            } else {
                pass.setHelpText("Enter your API Key");
            }
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        @Override
        public void setAccount(Account defaultAccount) {
            if (defaultAccount != null) {
                // name.setText(defaultAccount.getUser());
                pass.setText(defaultAccount.getPass());
            }
        }

        @Override
        public boolean validateInputs() {
            // final String userName = getUsername();
            // if (userName == null || !userName.trim().matches("^\\d{9}$")) {
            // idLabel.setForeground(Color.RED);
            // return false;
            // }
            // idLabel.setForeground(Color.BLACK);
            return getPassword() != null;
        }

        @Override
        public Account getAccount() {
            return new Account(null, getPassword());
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}
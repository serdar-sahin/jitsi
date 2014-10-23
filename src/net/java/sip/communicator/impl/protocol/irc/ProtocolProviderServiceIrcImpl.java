/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.irc;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

/**
 * An IRC implementation of the ProtocolProviderService.
 *
 * @author Loic Kempf
 * @author Stephane Remy
 * @author Danny van Heumen
 */
public class ProtocolProviderServiceIrcImpl
    extends AbstractProtocolProviderService
{
    /**
     * The default secure IRC server port.
     */
    private static final int DEFAULT_SECURE_IRC_PORT = 6697;

    /**
     * Logger.
     */
    private static final Logger LOGGER
        = Logger.getLogger(ProtocolProviderServiceIrcImpl.class);

    /**
     * The irc server.
     */
    private IrcStack ircstack = null;

    /**
     * The id of the account that this protocol provider represents.
     */
    private AccountID accountID = null;

    /**
     * We use this to lock access to initialization.
     */
    private final Object initializationLock = new Object();

    /**
     * The operation set managing multi user chat.
     */
    private OperationSetMultiUserChatIrcImpl multiUserChat;

    /**
     * The operation set for instant messaging.
     */
    private OperationSetBasicInstantMessagingIrcImpl instantMessaging;

    /**
     * The operation set for persistent presence.
     */
    private OperationSetPersistentPresenceIrcImpl persistentPresence;

    /**
     * Indicates whether or not the provider is initialized and ready for use.
     */
    private boolean isInitialized = false;

    /**
     * The icon corresponding to the irc protocol.
     */
    private final ProtocolIconIrcImpl ircIcon = new ProtocolIconIrcImpl();

    /**
     * Keeps our current registration state.
     */
    private RegistrationState currentRegistrationState
        = RegistrationState.UNREGISTERED;

    /**
     * The default constructor for the IRC protocol provider.
     */
    public ProtocolProviderServiceIrcImpl()
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("Creating a irc provider.");
        }
    }

    /**
     * Initializes the service implementation, and puts it in a sate where it
     * could operate with other services. It is strongly recommended that
     * properties in this Map be mapped to property names as specified by
     * <tt>AccountProperties</tt>.
     *
     * @param userID the user id of the IRC account we're currently
     * initializing
     * @param accountID the identifier of the account that this protocol
     * provider represents.
     *
     * @see net.java.sip.communicator.service.protocol.AccountID
     */
    protected void initialize(final String userID, final AccountID accountID)
    {
        synchronized (initializationLock)
        {
            this.accountID = accountID;

            //Initialize instant message transform support.
            addSupportedOperationSet(OperationSetInstantMessageTransform.class,
                new OperationSetInstantMessageTransformImpl());

            //Initialize the multi user chat support
            multiUserChat = new OperationSetMultiUserChatIrcImpl(this);

            addSupportedOperationSet(
                OperationSetMultiUserChat.class,
                multiUserChat);

            // Initialize basic instant messaging
            this.instantMessaging =
                new OperationSetBasicInstantMessagingIrcImpl(this);

            // Register basic instant messaging support.
            addSupportedOperationSet(OperationSetBasicInstantMessaging.class,
                this.instantMessaging);
            // Register basic instant messaging transport support.
            addSupportedOperationSet(
                OperationSetBasicInstantMessagingTransport.class,
                this.instantMessaging);

            //Initialize persistent presence
            persistentPresence =
                new OperationSetPersistentPresenceIrcImpl(this);

            // Register persistent presence support.
            addSupportedOperationSet(OperationSetPersistentPresence.class,
                persistentPresence);
            // Also register for (simple) presence support.
            addSupportedOperationSet(OperationSetPresence.class,
                persistentPresence);

            // TODO Implement OperationSetServerStoredAccountInfo so we can
            // suggest a display name to use when adding new chat rooms?

            final String user = getAccountID().getUserID();

            ircstack = new IrcStack(this, user, user, "Jitsi", user);

            isInitialized = true;
        }
    }

    /**
     * Get the Multi User Chat implementation.
     *
     * @return returns the Multi User Chat implementation
     */
    public OperationSetMultiUserChatIrcImpl getMUC()
    {
        return this.multiUserChat;
    }

    /**
     * Get the Basic Instant Messaging implementation.
     *
     * @return returns the Basic Instant Messaging implementation
     */
    public OperationSetBasicInstantMessagingIrcImpl getBasicInstantMessaging()
    {
        return this.instantMessaging;
    }

    /**
     * Get the Persistent Presence implementation.
     *
     * @return returns the Persistent Presence implementation.
     */
    public OperationSetPersistentPresenceIrcImpl getPersistentPresence()
    {
        return this.persistentPresence;
    }

    /**
     * Returns the AccountID that uniquely identifies the account represented
     * by this instance of the ProtocolProviderService.
     *
     * @return the id of the account represented by this provider.
     */
    public AccountID getAccountID()
    {
        return accountID;
    }

    /**
     * Returns the short name of the protocol that the implementation of this
     * provider is based upon (like SIP, Jabber, ICQ/AIM, or others for
     * example).
     *
     * @return a String containing the short name of the protocol this
     *   service is implementing (most often that would be a name in
     *   ProtocolNames).
     */
    public String getProtocolName()
    {
        return ProtocolNames.IRC;
    }

    /**
     * Returns the state of the registration of this protocol provider with
     * the corresponding registration service.
     *
     * @return ProviderRegistrationState
     */
    public RegistrationState getRegistrationState()
    {
        return currentRegistrationState;
    }

    /**
     * Starts the registration process.
     *
     * @param authority the security authority that will be used for
     *   resolving any security challenges that may be returned during the
     *   registration or at any moment while wer're registered.
     * @throws OperationFailedException with the corresponding code it the
     *   registration fails for some reason (e.g. a networking error or an
     *   implementation problem).
     */
    public void register(final SecurityAuthority authority)
        throws OperationFailedException
    {
        AccountID accountID = getAccountID();
        String serverAddress
            = accountID
                .getAccountPropertyString(
                    ProtocolProviderFactory.SERVER_ADDRESS);
        int serverPort
            = accountID
                .getAccountPropertyInt(
                    ProtocolProviderFactory.SERVER_PORT,
                    DEFAULT_SECURE_IRC_PORT);
        //Verify whether a password has already been stored for this account
        String serverPassword = IrcActivator.
            getProtocolProviderFactory().loadPassword(getAccountID());
        boolean autoNickChange =
            accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.AUTO_CHANGE_USER_NAME, true);
        boolean passwordRequired =
            !accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.NO_PASSWORD_REQUIRED, true);
        boolean secureConnection =
            accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.DEFAULT_ENCRYPTION, true);

        //if we don't - retrieve it from the user through the security authority
        if (serverPassword == null && passwordRequired)
        {
            //create a default credentials object
            UserCredentials credentials = new UserCredentials();
            credentials.setUserName(getAccountID().getUserID());

            //request a password from the user
            credentials
                = authority.obtainCredentials(
                    ProtocolNames.IRC,
                    credentials,
                    SecurityAuthority.AUTHENTICATION_REQUIRED);

            char[] pass = null;
            if (credentials != null)
            {
                // extract the password the user passed us.
                pass = credentials.getPassword();
            }

            // the user didn't provide us a password (canceled the operation)
            if (pass == null)
            {
                fireRegistrationStateChanged(
                    getRegistrationState(),
                    RegistrationState.UNREGISTERED,
                    RegistrationStateChangeEvent.REASON_USER_REQUEST, "");
                return;
            }
            serverPassword = new String(pass);

            //if the user indicated that the password should be saved, we'll ask
            //the proto provider factory to store it for us.
            if (credentials.isPasswordPersistent())
            {
                IrcActivator.getProtocolProviderFactory()
                    .storePassword(getAccountID(), serverPassword);
            }
        }

        try
        {
            this.ircstack.connect(serverAddress, serverPort, serverPassword,
                secureConnection, autoNickChange);
        }
        catch (Exception e)
        {
            throw new OperationFailedException(e.getMessage(), 0, e);
        }
    }

    /**
     * Makes the service implementation close all open sockets and release
     * any resources that it might have taken and prepare for
     * shutdown/garbage collection.
     */
    public void shutdown()
    {
        if (!isInitialized)
        {
            return;
        }
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("Killing the Irc Protocol Provider.");
        }

        try
        {
            synchronized (this.initializationLock)
            {
                unregister();
                this.ircstack.dispose();
                ircstack = null;
            }
        }
        catch (OperationFailedException ex)
        {
            // we're shutting down so we need to silence the exception here
            LOGGER.error("Failed to properly unregister before shutting down. "
                + getAccountID(), ex);
        }
    }

    /**
     * Ends the registration of this protocol provider with the current
     * registration service.
     *
     * @throws OperationFailedException with the corresponding code it the
     *   registration fails for some reason (e.g. a networking error or an
     *   implementation problem).
     */
    public void unregister() throws OperationFailedException
    {
        if (this.ircstack == null)
        {
            return;
        }
        this.ircstack.disconnect();
    }

    /**
     * {@inheritDoc}
     *
     * @return returns true in case of secure transport, or false if transport
     *         is not secure
     */
    @Override
    public boolean isSignalingTransportSecure()
    {
        final IrcConnection connection = this.ircstack.getConnection();
        return connection != null && connection.isSecureConnection();
    }

    /**
     * Returns the "transport" protocol of this instance used to carry the
     * control channel for the current protocol service.
     *
     * @return The "transport" protocol of this instance: TCP.
     */
    public TransportProtocol getTransportProtocol()
    {
        return TransportProtocol.TCP;
    }

    /**
     * Returns the icon for this protocol.
     *
     * @return the icon for this protocol
     */
    public ProtocolIcon getProtocolIcon()
    {
        return ircIcon;
    }

    /**
     * Returns the IRC stack implementation.
     *
     * @return the IRC stack implementation.
     */
    public IrcStack getIrcStack()
    {
        return ircstack;
    }

    /**
     * Returns the current registration state of this protocol provider.
     *
     * @return the current registration state of this protocol provider
     */
    protected RegistrationState getCurrentRegistrationState()
    {
        return currentRegistrationState;
    }

    /**
     * Sets the current registration state of this protocol provider.
     *
     * @param regState the new registration state to set
     */
    protected void setCurrentRegistrationState(
        final RegistrationState regState)
    {
        final RegistrationState oldState = this.currentRegistrationState;

        this.currentRegistrationState = regState;

        fireRegistrationStateChanged(
            oldState,
            this.currentRegistrationState,
            RegistrationStateChangeEvent.REASON_USER_REQUEST,
            null);
    }
}

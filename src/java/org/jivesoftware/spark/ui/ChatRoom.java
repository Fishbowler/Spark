/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.spark.ui;

import org.jivesoftware.resource.Res;
import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.debugger.EnhancedDebuggerWindow;
import org.jivesoftware.spark.ChatAreaSendField;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.component.BackgroundPanel;
import org.jivesoftware.spark.component.RolloverButton;
import org.jivesoftware.spark.plugin.ContextMenuListener;
import org.jivesoftware.spark.util.GraphicUtils;
import org.jivesoftware.spark.util.log.Log;
import org.jivesoftware.sparkimpl.settings.local.LocalPreferences;
import org.jivesoftware.sparkimpl.settings.local.SettingsManager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * The base implementation of all ChatRoom conversations. You would implement this class to have most types of Chat.
 */
public abstract class ChatRoom extends BackgroundPanel implements ActionListener, PacketListener, DocumentListener, ConnectionListener, FocusListener {
    private final JPanel chatPanel;
    private final JSplitPane splitPane;
    private JSplitPane verticalSplit;

    private final JLabel notificationLabel;
    private final TranscriptWindow transcriptWindow;
    private final ChatAreaSendField chatAreaButton;
    private final ChatToolBar toolbar;
    private final JScrollPane textScroller;
    private final JPanel bottomPanel;
    private final JPanel editorBar;
    private JPanel chatWindowPanel;

    private int unreadMessageCount;

    private boolean mousePressed;

    private List<ChatRoomClosingListener> closingListeners = new CopyOnWriteArrayList<ChatRoomClosingListener>();


    private ChatRoomTransferHandler transferHandler;

    private final List<String> packetIDList;
    private final List<MessageListener> messageListeners;
    private List<Message> transcript;
    private List<FileDropListener> fileDropListeners;

    /**
     * Initializes the base layout and base background color.
     */
    protected ChatRoom() {
        chatPanel = new JPanel(new GridBagLayout());
        transcriptWindow = new TranscriptWindow();
        splitPane = new JSplitPane();
        packetIDList = new ArrayList<String>();
        notificationLabel = new JLabel();
        toolbar = new ChatToolBar();
        bottomPanel = new JPanel();

        messageListeners = new ArrayList<MessageListener>();
        transcript = new ArrayList<Message>();
        editorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 1));
        fileDropListeners = new ArrayList<FileDropListener>();

        transcriptWindow.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                getChatInputEditor().requestFocus();
            }

            public void mouseReleased(MouseEvent e) {
                if (transcriptWindow.getSelectedText() == null) {
                    getChatInputEditor().requestFocus();
                }
            }
        });

        chatAreaButton = new ChatAreaSendField(SparkRes.getString(SparkRes.SEND)) {
            public Dimension getPreferredSize() {
                Dimension dim = super.getPreferredSize();

                int windowHeight = getChatRoom().getHeight();

                if (dim.getHeight() > windowHeight - 200) {
                    dim.height = windowHeight - 200;
                }

                return dim;
            }
        };


        textScroller = new JScrollPane(transcriptWindow);

        textScroller.getVerticalScrollBar().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                mousePressed = true;
            }

            public void mouseReleased(MouseEvent e) {
                mousePressed = false;
            }
        });

        textScroller.setBackground(transcriptWindow.getBackground());
        textScroller.getViewport().setBackground(Color.white);
        transcriptWindow.setBackground(Color.white);

        getChatInputEditor().setSelectedTextColor((Color)UIManager.get("ChatInput.SelectedTextColor"));
        getChatInputEditor().setSelectionColor((Color)UIManager.get("ChatInput.SelectionColor"));


        init();

        // Initally, set the right pane to null to keep it empty.
        getSplitPane().setRightComponent(null);

        notificationLabel.setIcon(SparkRes.getImageIcon(SparkRes.BLANK_IMAGE));


        getTranscriptWindow().addContextMenuListener(new ContextMenuListener() {
            public void poppingUp(Object component, JPopupMenu popup) {
                Action saveAction = new AbstractAction() {
                    public void actionPerformed(ActionEvent actionEvent) {
                        saveTranscript();
                    }
                };
                saveAction.putValue(Action.NAME, "Save");
                saveAction.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.SAVE_AS_16x16));


                popup.add(saveAction);
            }

            public void poppingDown(JPopupMenu popup) {

            }

            public boolean handleDefaultAction(MouseEvent e) {
                return false;
            }
        });

        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F12"), "showDebugger");
        this.getActionMap().put("showDebugger", new AbstractAction("showDebugger") {
            public void actionPerformed(ActionEvent evt) {
                EnhancedDebuggerWindow window = EnhancedDebuggerWindow.getInstance();
                window.setVisible(true);
            }
        });


        transferHandler = new ChatRoomTransferHandler(this);

        getTranscriptWindow().setTransferHandler(transferHandler);
        getChatInputEditor().setTransferHandler(transferHandler);

        add(toolbar, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

        // Add Connection Listener
        SparkManager.getConnection().addConnectionListener(this);

        // Add Focus Listener
        addFocusListener(this);
    }

    // Setup base layout.
    private void init() {
        setLayout(new GridBagLayout());

        // Remove Default Beveled Borders
        splitPane.setBorder(null);
        splitPane.setOneTouchExpandable(false);

        // Add Vertical Split Pane
        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        add(verticalSplit, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        verticalSplit.setBorder(null);
        verticalSplit.setOneTouchExpandable(false);

        verticalSplit.setTopComponent(splitPane);

        textScroller.setAutoscrolls(true);

        // Speed up scrolling. It was way too slow.
        textScroller.getVerticalScrollBar().setBlockIncrement(50);
        textScroller.getVerticalScrollBar().setUnitIncrement(20);

        chatWindowPanel = new JPanel();
        chatWindowPanel.setLayout(new GridBagLayout());
        chatWindowPanel.add(textScroller, new GridBagConstraints(0, 10, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        chatWindowPanel.setOpaque(false);

        // Layout Components
        chatPanel.add(chatWindowPanel, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 5, 0, 5), 0, 0));

        // Add Chat Panel to Split Pane
        splitPane.setLeftComponent(chatPanel);

        // Add edit buttons to Chat Room
        editorBar.setOpaque(false);
        chatPanel.setOpaque(false);


        editorBar.add(new JSeparator(JSeparator.VERTICAL));

        bottomPanel.setOpaque(false);
        splitPane.setOpaque(false);
        bottomPanel.setLayout(new GridBagLayout());
        bottomPanel.add(chatAreaButton, new GridBagConstraints(0, 1, 5, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 5, 5, 5), 0, 15));
        bottomPanel.add(editorBar, new GridBagConstraints(0, 0, 5, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 0, 5), 0, 0));

        // Set bottom panel border
        bottomPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(197, 213, 230)));
        verticalSplit.setOpaque(false);

        verticalSplit.setBottomComponent(bottomPanel);
        verticalSplit.setResizeWeight(1.0);
        verticalSplit.setDividerSize(2);

        // Add listener to send button
        chatAreaButton.getButton().addActionListener(this);

        // Add Key Listener to Send Field
        getChatInputEditor().getDocument().addDocumentListener(this);

        // Add Key Listener to Send Field
        getChatInputEditor().addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                checkForEnter(e);
            }
        });

        getChatInputEditor().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl F4"), "closeTheRoom");
        getChatInputEditor().getActionMap().put("closeTheRoom", new AbstractAction("closeTheRoom") {
            public void actionPerformed(ActionEvent evt) {
                // Leave this chat.
                closeChatRoom();
            }
        });
    }


    // I would normally use the command pattern, but
    // have no real use when dealing with just a couple options.
    public void actionPerformed(ActionEvent e) {
        sendMessage();

        // Clear send field and disable send button
        getChatInputEditor().clear();
        chatAreaButton.getButton().setEnabled(false);
    }

    /**
     * Creates and sends a message object from the text in
     * the Send Field, using the default nickname specified in your
     * Chat Preferences.
     */
    protected abstract void sendMessage();

    /**
     * Creates a Message object from the given text and delegates to the room
     * for sending.
     *
     * @param text the text to send.
     */
    protected abstract void sendMessage(String text);

    /**
     * Sends the current message.
     *
     * @param message - the message to send.
     */
    public abstract void sendMessage(Message message);

    /**
     * Returns the nickname of the current agent as specified in Chat
     * Preferences.
     *
     * @return the nickname of the agent.
     */
    public String getNickname() {
        LocalPreferences pref = SettingsManager.getLocalPreferences();
        return pref.getNickname();
    }


    /**
     * The main entry point when receiving any messages. This will
     * either handle a message from a customer or delegate itself
     * as an agent handler.
     *
     * @param message - the message receieved.
     */
    public void insertMessage(Message message) {
        // Fire Message Filters
        SparkManager.getChatManager().filterIncomingMessage(this, message);

        SparkManager.getChatManager().fireGlobalMessageReceievedListeners(this, message);

        addToTranscript(message, true);

        fireMessageReceived(message);
    }


    /**
     * Add a <code>ChatResponse</chat> to the current discussion chat area.
     *
     * @param message    the message to add to the transcript list
     * @param updateDate true if you wish the date label to be updated with the
     *                   date and time the message was received.
     */
    public void addToTranscript(Message message, boolean updateDate) {
        // Create message to persist.
        final Message newMessage = new Message();
        newMessage.setTo(message.getTo());
        newMessage.setFrom(message.getFrom());
        newMessage.setBody(message.getBody());
        newMessage.setProperty("date", new Date());

        transcript.add(newMessage);

        // Add current date if this is the current agent
        if (updateDate && transcriptWindow.getLastUpdated() != null) {
            // Set new label date
            notificationLabel.setIcon(SparkRes.getImageIcon(SparkRes.SMALL_ABOUT_IMAGE));
            notificationLabel.setText(Res.getString("message.last.message.received", SparkManager.DATE_SECOND_FORMATTER.format(transcriptWindow.getLastUpdated())));
        }

        scrollToBottom();
    }

    /**
     * Adds a new message to the transcript history.
     *
     * @param to   who the message is to.
     * @param from who the message was from.
     * @param body the body of the message.
     * @param date when the message was received.
     */
    public void addToTranscript(String to, String from, String body, Date date) {
        final Message newMessage = new Message();
        newMessage.setTo(to);
        newMessage.setFrom(from);
        newMessage.setBody(body);
        newMessage.setProperty("date", date);
        transcript.add(newMessage);
    }

    /**
     * Scrolls the chat window to the bottom.
     */
    public void scrollToBottom() {
        if (mousePressed) {
            return;
        }

        int lengthOfChat = transcriptWindow.getDocument().getLength();
        transcriptWindow.setCaretPosition(lengthOfChat);

        try {
            JScrollBar scrollBar = textScroller.getVerticalScrollBar();
            scrollBar.setValue(scrollBar.getMaximum());
        }
        catch (Exception e) {
            Log.error(e);
        }
    }


    /**
     * Checks to see if the Send button should be enabled.
     *
     * @param e - the documentevent to react to.
     */
    protected void checkForText(DocumentEvent e) {
        final int length = e.getDocument().getLength();
        if (length > 0) {
            chatAreaButton.getButton().setEnabled(true);
        }
        else {
            chatAreaButton.getButton().setEnabled(false);
        }

        verticalSplit.setDividerLocation(-1);
    }

    /**
     * Requests valid focus to the SendField.
     */
    public void positionCursor() {
        getChatInputEditor().setCaretPosition(getChatInputEditor().getCaretPosition());
        chatAreaButton.getChatInputArea().requestFocusInWindow();
    }


    /**
     * Disable the chat room. This is called when a chat has been either transfered over or
     * the customer has left the chat room.
     */
    public abstract void leaveChatRoom();


    /**
     * Process incoming packets.
     *
     * @param packet - the packet to process
     */
    public void processPacket(Packet packet) {
    }


    /**
     * Returns the SendField component.
     *
     * @return the SendField ChatSendField.
     */
    public ChatInputEditor getChatInputEditor() {
        return chatAreaButton.getChatInputArea();
    }

    /**
     * Returns the chatWindow components.
     *
     * @return the ChatWindow component.
     */
    public TranscriptWindow getTranscriptWindow() {
        return transcriptWindow;
    }


    /**
     * Checks to see if enter was pressed and validates room.
     *
     * @param e the KeyEvent
     */
    private void checkForEnter(KeyEvent e) {
        final KeyStroke keyStroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
        if (!keyStroke.equals(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK)) &&
            e.getKeyChar() == KeyEvent.VK_ENTER) {
            e.consume();
            sendMessage();
            getChatInputEditor().setText("");
            getChatInputEditor().setCaretPosition(0);
        }
        else if (keyStroke.equals(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK))) {
            final Document document = getChatInputEditor().getDocument();
            try {
                document.insertString(getChatInputEditor().getCaretPosition(), "\n", null);
                getChatInputEditor().requestFocusInWindow();
                chatAreaButton.getButton().setEnabled(true);
            }
            catch (BadLocationException badLoc) {
                Log.error("Error when checking for enter:", badLoc);
            }
        }
    }

    /**
     * Add a {@link MessageListener} to the current ChatRoom.
     *
     * @param listener - the MessageListener to add to the current ChatRoom.
     */
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    /**
     * Remove the specified {@link MessageListener } from the current ChatRoom.
     *
     * @param listener - the MessageListener to remove from the current ChatRoom.
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * Notifies all message listeners that
     *
     * @param message the message received.
     */
    private void fireMessageReceived(Message message) {
        for (MessageListener messageListener : messageListeners) {
            messageListener.messageReceived(this, message);
        }
    }

    /**
     * Notifies all <code>MessageListener</code> that a message has been sent.
     *
     * @param message the message sent.
     */
    protected void fireMessageSent(Message message) {
        for (MessageListener messageListener : messageListeners) {
            messageListener.messageSent(this, message);
        }
    }

    /**
     * Returns a map of the current Chat Transcript which is a list of all
     * ChatResponses and their order. You should retrieve this map to get
     * any current chat transcript state.
     *
     * @return - the map of current chat responses.
     */
    public List<Message> getTranscripts() {
        return transcript;
    }

    /**
     * Disables the ChatRoom toolbar.
     */
    public void disableToolbar() {
        final int count = editorBar.getComponentCount();
        for (int i = 0; i < count; i++) {
            final Object o = editorBar.getComponent(i);
            if (o instanceof RolloverButton) {
                final RolloverButton rb = (RolloverButton)o;
                rb.setEnabled(false);
            }
        }
    }

    /**
     * Enable the ChatRoom toolbar.
     */
    public void enableToolbar() {
        final int count = editorBar.getComponentCount();
        for (int i = 0; i < count; i++) {
            final Object o = editorBar.getComponent(i);
            if (o instanceof RolloverButton) {
                final RolloverButton rb = (RolloverButton)o;
                rb.setEnabled(true);
            }
        }
    }


    /**
     * Checks to see if the Send Button should be enabled depending on the
     * current update in SendField.
     *
     * @param event the DocumentEvent from the sendField.
     */
    public void removeUpdate(DocumentEvent event) {
        checkForText(event);
    }

    /**
     * Checks to see if the Send button should be enabled.
     *
     * @param docEvent the document event.
     */
    public void changedUpdate(DocumentEvent docEvent) {
        // Do nothing.
    }

    /**
     * Return the splitpane used in this chat room.
     *
     * @return the splitpane used in this chat room.
     */
    public JSplitPane getSplitPane() {
        return splitPane;
    }

    /**
     * Returns the ChatPanel that contains the ChatWindow and SendField.
     *
     * @return the ChatPanel.
     */
    public JPanel getChatPanel() {
        return chatPanel;
    }

    /**
     * Close the ChatRoom.
     */
    public void closeChatRoom() {
        fireClosingListeners();

        // Remove Connection Listener
        SparkManager.getConnection().removeConnectionListener(this);
        getTranscriptWindow().setTransferHandler(null);
        getChatInputEditor().setTransferHandler(null);

        transferHandler = null;

        packetIDList.clear();
        messageListeners.clear();
        fileDropListeners.clear();
        getChatInputEditor().close();
    }

    /**
     * Get the <code>Icon</code> to be used in the tab holding
     * this ChatRoom.
     *
     * @return - <code>Icon</code> to use
     */
    public abstract Icon getTabIcon();

    /**
     * Get the roomname to use for this ChatRoom.
     *
     * @return - the Roomname of this ChatRoom.
     */
    public abstract String getRoomname();

    /**
     * Get the title to use in the tab holding this ChatRoom.
     *
     * @return - the title to use.
     */
    public abstract String getTabTitle();

    /**
     * Returns the title of this room to use. The title
     * will be used in the title bar of the ChatRoom.
     *
     * @return - the title of this ChatRoom.
     */
    public abstract String getRoomTitle();

    /**
     * Returns the <code>Message.Type</code> specific to this
     * chat room.
     * GroupChat is Message.Type.GROUP_CHAT
     * Normal Chat is Message.TYPE.NORMAL
     *
     * @return the ChatRooms Message.TYPE
     */
    public abstract Message.Type getChatType();


    /**
     * Returns whether or not this ChatRoom is active. Note: carrying
     * a conversation rather than being disabled, as it would be
     * transcript mode.
     *
     * @return true if the chat room is active.
     */
    public abstract boolean isActive();


    /**
     * Returns the notification label. The notification label notifies the
     * user of chat room activity, such as the date of the last message
     * and typing notifications.
     *
     * @return the notification label.
     */
    public JLabel getNotificationLabel() {
        return notificationLabel;
    }

    /**
     * Adds a packetID to the packedIDList. The packetIDLlist
     * keeps track of all messages coming into the chatroom.
     *
     * @param packetID the packetID to add.
     */
    public void addPacketID(String packetID) {
        packetIDList.add(packetID);
    }

    /**
     * Checks if the packetID has already been used.
     *
     * @param packetID the packetID to check for.
     * @return true if the packetID already exists.
     */
    public boolean packetIDExists(String packetID) {
        return packetIDList.contains(packetID);
    }

    /**
     * Returns this instance of the chatroom.
     *
     * @return the current ChatRoom instance.
     */
    public ChatRoom getChatRoom() {
        return this;
    }

    /**
     * Returns the toolbar used on top of the chat room.
     *
     * @return the toolbar used on top of this chat room.
     */
    public ChatToolBar getToolBar() {
        return toolbar;
    }


    public void insertUpdate(DocumentEvent e) {
        // Meant to be overriden
        checkForText(e);
    }


    /**
     * Override to save transcript in preferred room style.
     */
    public void saveTranscript() {
        getTranscriptWindow().saveTranscript(getTabTitle() + ".html", getTranscripts(), null);
    }


    /**
     * Returns the button panel. The Button Panel contains all tool items
     * above the send field.
     *
     * @return the chat's button panel.
     */
    public JPanel getSendFieldToolbar() {
        return editorBar;
    }

    /**
     * Used for the top toolbar.
     */
    public class ChatToolBar extends JPanel {
        private JPanel buttonPanel;


        /**
         * Default Constructor.
         */
        public ChatToolBar() {
            buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));

            // Set Layout
            setLayout(new GridBagLayout());

            buttonPanel.setOpaque(false);
            add(buttonPanel, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
            setOpaque(false);
        }

        /**
         * Adds a new ChatRoomButton the CommandBar.
         *
         * @param button the button.
         */
        public void addChatRoomButton(ChatRoomButton button) {
            buttonPanel.add(button);

            // Make all JButtons the same size
            Component[] comps = buttonPanel.getComponents();
            final int no = comps != null ? comps.length : 0;

            final List<Component> buttons = new ArrayList<Component>();
            for (int i = 0; i < no; i++) {
                Component component = comps[i];
                if (component instanceof JButton) {
                    buttons.add(component);
                }
            }

            GraphicUtils.makeSameSize((JComponent[])buttons.toArray(new JComponent[buttons.size()]));
        }

        /**
         * Removes the ChatRoomButton from the CommandBar.
         *
         * @param button the button.
         */
        public void removeChatRoomButton(ChatRoomButton button) {
            buttonPanel.remove(button);
        }
    }

    /**
     * Returns the number of unread messages in this ChatRoom.
     *
     * @return the number of unread messages.
     */
    public int getUnreadMessageCount() {
        return unreadMessageCount;
    }

    /**
     * Increases the number of unread messages by 1.
     */
    public void increaseUnreadMessageCount() {
        unreadMessageCount++;
    }

    /**
     * Resets the number of unread messages.
     */
    public void clearUnreadMessageCount() {
        unreadMessageCount = 0;
    }

    /**
     * Returns the bottom panel used in the ChatRoom.
     *
     * @return the bottomPane;
     */
    public JPanel getBottomPanel() {
        return bottomPanel;
    }

    /**
     * Returns the Container which holds the ChatWindow.
     *
     * @return the Container.
     */
    public JPanel getChatWindowPanel() {
        return chatWindowPanel;
    }

    /**
     * Adds a new <code>FileDropListener</code> to allow for Drag and Drop notifications
     * of objects onto the ChatWindow.
     *
     * @param listener the listener.
     */
    public void addFileDropListener(FileDropListener listener) {
        fileDropListeners.add(listener);
    }

    /**
     * Remove the <code>FileDropListener</code> from ChatRoom.
     *
     * @param listener the listener.
     */
    public void removeFileDropListener(FileDropListener listener) {
        fileDropListeners.remove(listener);
    }

    /**
     * Notify all users that a collection of files has been dropped onto the ChatRoom.
     *
     * @param files the files dropped.
     */
    public void fireFileDropListeners(Collection files) {
        for (FileDropListener fileDropListener : fileDropListeners) {
            fileDropListener.filesDropped(files, this);
        }
    }

    /**
     * Returns the panel which contains the toolbar items, such as spell checker.
     *
     * @return the panel which contains the lower toolbar items.
     */
    public JPanel getEditorBar() {
        return editorBar;
    }

    /**
     * Adds a <code>ChatRoomClosingListener</code> to this ChatRoom. A ChatRoomClosingListener
     * is notified whenever this room is closing.
     *
     * @param listener the ChatRoomClosingListener.
     */
    public void addClosingListener(ChatRoomClosingListener listener) {
        closingListeners.add(listener);
    }

    /**
     * Removes a <code>ChatRoomClosingListener</code> from this ChatRoom.
     *
     * @param listener the ChatRoomClosingListener.
     */
    public void removeClosingListener(ChatRoomClosingListener listener) {
        closingListeners.remove(listener);
    }

    /**
     * Notifies all <code>ChatRoomClosingListener</code> that this ChatRoom is closing.
     */
    private void fireClosingListeners() {
        for (ChatRoomClosingListener chatRoomClosingListener : closingListeners) {
            chatRoomClosingListener.closing();
            removeClosingListener(chatRoomClosingListener);
        }
    }

    /**
     * Returns the ScrollPane that contains the TranscriptWindow.
     *
     * @return the <code>TranscriptWindow</code> ScrollPane.
     */
    public JScrollPane getScrollPaneForTranscriptWindow() {
        return textScroller;
    }

    /**
     * Return the "Send" button.
     *
     * @return the send button.
     */
    public JButton getSendButton() {
        return chatAreaButton.getButton();
    }

    /**
     * Returns the VerticalSplitPane used in this ChatRoom.
     *
     * @return the VerticalSplitPane.
     */
    public JSplitPane getVerticalSlipPane() {
        return verticalSplit;
    }


    public void focusGained(FocusEvent focusEvent) {
        validate();
        invalidate();
        repaint();

        verticalSplit.setDividerLocation(-1);
    }

    public void focusLost(FocusEvent focusEvent) {
    }


    /**
     * Implementation of this method should return the last time this chat room
     * sent or recieved a message.
     *
     * @return the last time (in system milliseconds) that the room last recieved a message.
     */
    public abstract long getLastActivity();


    public void connectionClosed() {
    }

    public void connectionClosedOnError(Exception e) {
    }

    public void reconnectingIn(int seconds) {
    }

    public void reconnectionSuccessful() {
    }

    public void reconnectionFailed(Exception e) {
    }
}



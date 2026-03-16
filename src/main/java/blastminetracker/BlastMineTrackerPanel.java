package blastminetracker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.*;
import java.awt.Image;

import java.text.DecimalFormat;
import javax.inject.Inject;

import javax.swing.*;
import javax.swing.JScrollPane;
import javax.swing.border.Border;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.EquipmentInventorySlot;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;


import blastminetracker.BlastMineTrackerSessionStore.SessionSnapshot;


class BlastMineTrackerPanel extends PluginPanel
{
    private final Client client;

    private final DecimalFormat INT_FMT = new DecimalFormat("#,##0");
    private final DecimalFormat PCT_FMT = new DecimalFormat("0.0");
    private final DecimalFormat PCT_100_FMT = new DecimalFormat("0");

    private final JLabel sessionTimeValue = buildValueLabel();
    private final JLabel dynamiteUsedValue = buildValueLabel();

    private final JLabel excavationXPValue = buildValueLabel();
    private final JLabel xpToCollectValue = buildValueLabel();
    private final JLabel xpPerHourValue = buildValueLabel();

    private final JLabel grossProfitValue = buildValueLabel();
    private final JLabel dynamiteCostValue = buildValueLabel();
    private final JLabel netProfitValue = buildValueLabel();
    private final JLabel netGpPerHourValue = buildValueLabel();

    private JPanel currentSessionCard;

    private final ItemManager itemManager;

    private int dynamiteUsed;
    private double excavationXPGained;
    private int ticksElapsed;
    private boolean activeSession;
    private String sessionStartedAt;

    private final JPanel pastSessionsContainer = new JPanel()
    {
        @Override
        public Dimension getPreferredSize()
        {
            Dimension d = super.getPreferredSize();
            Container parent = getParent();
            if (parent instanceof JViewport)
            {
                d.width = ((JViewport) parent).getWidth();
            }
            return d;
        }
    };

    private JScrollPane pastSessionsScroll;
    private java.util.List<SessionSnapshot> previousSessions = new java.util.ArrayList<>();
    private final BlastMineTrackerSessionStore sessionStore;

    private JPanel currentOreCountPanel;
    private final Map<Integer, Integer> currentOreAmounts = new LinkedHashMap<>();
    private final Map<Integer, JLabel> currentOrePercentLabels = new LinkedHashMap<>();
    private static final int ORE_TILE_SIZE = 41; // 42 too big for when past sessions become scrollable

    private final Map<Integer, JLabel> currentOreAmountLabels = new LinkedHashMap<>();

    private static final int[] LOVAKENGJ_ORE_IDS = new int[]
            {
                    ItemID.COAL,
                    ItemID.GOLD_ORE,
                    ItemID.MITHRIL_ORE,
                    ItemID.ADAMANTITE_ORE,
                    ItemID.RUNITE_ORE
            };


    private Component buildDivider()
    {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        wrapper.add(sep, BorderLayout.CENTER);

        return wrapper;
    }


    @Inject
    BlastMineTrackerPanel(
            final ItemManager itemManager,
            final Client client,
            final BlastMineTrackerSessionStore sessionStore){
        super(false);

        this.itemManager = itemManager;
        this.client = client;
        this.sessionStore = sessionStore;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));

        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JLabel currentSessionLabel = new JLabel("Current Session");
        currentSessionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        currentSessionLabel.setFont(FontManager.getRunescapeBoldFont());
        currentSessionLabel.setForeground(Color.WHITE);

        currentSessionCard = buildCurrentSessionCard();
        currentSessionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel footerText = new JLabel(
                "<html><div style='width:150px;'>Start a session by excavating<br>End a session by collecting ores</div></html>");
        footerText.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerText.setForeground(Color.LIGHT_GRAY);

        JLabel pastSessionsLabel = new JLabel("Past Sessions");
        pastSessionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pastSessionsLabel.setFont(FontManager.getRunescapeBoldFont());
        pastSessionsLabel.setForeground(Color.WHITE);

        int headerTextIndent = 8;
        topPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        topPanel.add(wrapWithLeftPadding(currentSessionLabel, headerTextIndent));
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        topPanel.add(currentSessionCard);
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        topPanel.add(wrapWithLeftPadding(footerText, headerTextIndent));
        topPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        topPanel.add(wrapWithLeftPadding(pastSessionsLabel, headerTextIndent));
        topPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        pastSessionsContainer.setLayout(new BoxLayout(pastSessionsContainer, BoxLayout.Y_AXIS));
        pastSessionsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        pastSessionsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        pastSessionsScroll = new JScrollPane(pastSessionsContainer);
        pastSessionsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pastSessionsScroll.setViewportBorder(null);
        pastSessionsScroll.getViewport().setBorder(null);

        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.setOpaque(false);
        topWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        topWrapper.add(topPanel, BorderLayout.CENTER);

        add(topWrapper, BorderLayout.NORTH);

        add(pastSessionsScroll, BorderLayout.CENTER);
    }

    private JPanel wrapWithLeftPadding(JComponent component, int leftPadding)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, leftPadding, 0, 0));
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildSessionCard(
            JLabel sessionTime,
            JLabel dynamiteUsed,
            JLabel excavationXP,
            JLabel xpToCollect,
            JLabel xpPerHour,
            JLabel grossProfit,
            JLabel dynamiteCost,
            JLabel netProfit,
            JLabel netGpPerHour,
            Map<Integer, Integer> oreAmounts,
            Map<Integer, JLabel> oreAmountLabels,
            Map<Integer, JLabel> orePercentLabels,
            boolean storeAsCurrentOrePanel)
    {
        JPanel card = buildCardPanel();

        card.add(buildStatRow("Session Time", sessionTime));
        card.add(buildStatRow("Dynamite Used", dynamiteUsed));

        card.add(buildDivider());
        card.add(buildStatRow("Excavation XP", excavationXP));
        card.add(buildStatRow("Collection XP", xpToCollect));
        card.add(buildStatRow("XP / Hour", xpPerHour));

        card.add(buildDivider());
        card.add(buildStatRow("Gross Profit", grossProfit));
        card.add(buildStatRow("Dynamite Cost", dynamiteCost));
        card.add(buildStatRow("Net Profit", netProfit));
        card.add(buildStatRow("Net Profit / Hour", netGpPerHour));

        card.add(buildDivider());

        JPanel orePanel = buildOreCountsRow(oreAmounts, oreAmountLabels, orePercentLabels);
        card.add(orePanel);

        if (storeAsCurrentOrePanel)
        {
            currentOreCountPanel = orePanel;
            currentOreCountPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        return card;
    }

    private JPanel buildCurrentSessionCard()
    {
        resetCurrentOreAmounts();

        return buildSessionCard(
                sessionTimeValue,
                dynamiteUsedValue,
                excavationXPValue,
                xpToCollectValue,
                xpPerHourValue,
                grossProfitValue,
                dynamiteCostValue,
                netProfitValue,
                netGpPerHourValue,
                currentOreAmounts,
                currentOreAmountLabels,
                currentOrePercentLabels,
                true
        );
    }

    private JPanel buildPastSessionCard(SessionSnapshot snapshot)
    {
        JPanel card = buildSessionCard(
                buildStaticValueLabel(snapshot.getSessionTimeText()),
                buildStaticValueLabel(INT_FMT.format(snapshot.getDynamiteUsed())),
                buildStaticValueLabel(INT_FMT.format((int) snapshot.getExcavationXPGained())),
                buildStaticValueLabel(nullToZero(snapshot.getXpToCollectText())),
                buildStaticValueLabel(nullToZero(snapshot.getXpPerHourText())),
                buildStaticValueLabel(nullToZero(snapshot.getGrossProfitText())),
                buildStaticValueLabel(nullToZero(snapshot.getDynamiteCostText())),
                buildStaticValueLabel(nullToZero(snapshot.getNetProfitText())),
                buildStaticValueLabel(nullToZero(snapshot.getNetGpPerHourText())),
                snapshot.getOreAmounts(),
                null,
                null,
                false
        );
        Border originalBorder = card.getBorder();
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 6),
                originalBorder
        ));

        return card;
    }

    private JPanel buildCardPanel()
    {
        JPanel panel = new JPanel()
        {
            @Override
            public Dimension getMaximumSize()
            {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };

        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        return panel;
    }

    private JLabel buildStaticValueLabel(String text)
    {
        JLabel label = buildValueLabel();
        label.setText(text == null ? "0" : text);
        return label;
    }

    private String nullToZero(String value)
    {
        return value == null || value.isEmpty() ? "0" : value;
    }

    private JPanel buildStatRow(String labelText, JLabel valueLabel)
    {
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.LIGHT_GRAY);
        label.setFont(FontManager.getRunescapeFont());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        label.setHorizontalAlignment(SwingConstants.LEFT);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        Dimension valueSize = new Dimension(75, 20);
        valueLabel.setPreferredSize(valueSize);

        row.add(label, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    private JLabel buildValueLabel()
    {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeBoldFont());
        return label;
    }


    private JPanel buildOreCountsRow(
            Map<Integer, Integer> oreAmounts,
            Map<Integer, JLabel> liveAmountLabels,
            Map<Integer, JLabel> livePercentLabels)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        if (oreAmounts == null)
        {
            oreAmounts = new LinkedHashMap<>();
        }

        if (liveAmountLabels != null)
        {
            liveAmountLabels.clear();
        }
        if (livePercentLabels != null)
        {
            livePercentLabels.clear();
        }

        int totalOres = 0;
        for (int itemId : LOVAKENGJ_ORE_IDS)
        {
            totalOres += oreAmounts.getOrDefault(itemId, 0);
        }

        for (int itemId : LOVAKENGJ_ORE_IDS)
        {
            int amount = oreAmounts.getOrDefault(itemId, 0);

            JLabel amountLabel = buildValueLabel();
            amountLabel.setText(INT_FMT.format(amount));

            String percentString = getPercentString(amount, totalOres);
            JLabel percentLabel = new JLabel(percentString);

            row.add(buildOreCountTile(itemId, amountLabel, percentLabel));

            if (liveAmountLabels != null)
            {
                liveAmountLabels.put(itemId, amountLabel);
            }
            if (livePercentLabels != null)
            {
                livePercentLabels.put(itemId, percentLabel);
            }
        }

        wrapper.add(row, BorderLayout.WEST);
        return wrapper;
    }

    private String getPercentString(int amount, int totalOres){
        double percent = totalOres == 0 ? 0.0 : (100.0 * amount / totalOres);
        String percentString;
        if (percent == 100.0){
            percentString = PCT_100_FMT.format(percent);
        }else{
            percentString = PCT_FMT.format(percent);
        }
        return percentString + "%";
    }

    private JPanel buildOreCountTile(int itemId, JLabel amountLabel, JLabel percentLabel)
    {
        JPanel tile = new JPanel();
        tile.setOpaque(false);
        tile.setLayout(new OverlayLayout(tile));
        tile.setPreferredSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));
        tile.setMinimumSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));
        tile.setMaximumSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));

        JLabel iconLabel = new JLabel();
        iconLabel.setOpaque(false);
        iconLabel.setAlignmentX(0.0f);
        iconLabel.setAlignmentY(0.0f);

        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null)
        {
            image.onLoaded(() ->
            {
                iconLabel.setIcon(new ImageIcon(
                        image.getScaledInstance(ORE_TILE_SIZE, ORE_TILE_SIZE, Image.SCALE_SMOOTH)
                ));
                tile.revalidate();
                tile.repaint();
            });
        }

        amountLabel.setHorizontalAlignment(SwingConstants.LEFT);
        amountLabel.setForeground(Color.WHITE);
        amountLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));

        percentLabel.setForeground(Color.WHITE);
        percentLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
        percentLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

        textPanel.setAlignmentX(0.0f);
        textPanel.setAlignmentY(0.0f);

        textPanel.setPreferredSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));
        textPanel.setMinimumSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));
        textPanel.setMaximumSize(new Dimension(ORE_TILE_SIZE, ORE_TILE_SIZE));

        Dimension textSize = new Dimension(ORE_TILE_SIZE, 12);

        amountLabel.setPreferredSize(textSize);
        amountLabel.setMinimumSize(textSize);
        amountLabel.setMaximumSize(textSize);

        percentLabel.setPreferredSize(textSize);
        percentLabel.setMinimumSize(textSize);
        percentLabel.setMaximumSize(textSize);

        amountLabel.setAlignmentX(0.0f);
        percentLabel.setAlignmentX(0.0f);
        percentLabel.setAlignmentY(0.0f);

        textPanel.add(amountLabel);
        textPanel.add(Box.createVerticalStrut(16));
        textPanel.add(percentLabel);

        tile.add(textPanel);
        tile.add(iconLabel);

        return tile;
    }


    private void updateCurrentOreCountPanel()
    {
        int totalOres = 0;
        for (int itemId : LOVAKENGJ_ORE_IDS)
        {
            totalOres += currentOreAmounts.getOrDefault(itemId, 0);
        }

        for (int itemId : LOVAKENGJ_ORE_IDS)
        {
            int amount = currentOreAmounts.getOrDefault(itemId, 0);

            JLabel amountLabel = currentOreAmountLabels.get(itemId);
            if (amountLabel != null)
            {
                amountLabel.setText(INT_FMT.format(amount));
            }

            JLabel percentLabel = currentOrePercentLabels.get(itemId);
            if (percentLabel != null)
            {
                String percentString = getPercentString(amount, totalOres);
                percentLabel.setText(percentString);
            }
        }

        currentOreCountPanel.revalidate();
        currentOreCountPanel.repaint();
    }

    public void refreshState(){
        SessionSnapshot currentSessionSnapshot = sessionStore.loadCurrentSession();

        if (currentSessionSnapshot != null){
            loadStateFromCurrentSession(currentSessionSnapshot);
        }else{
            resetState();
        }

        loadPreviousSessions();
        displayPreviousSessions();
    }

    private void startSession()
    {
        activeSession = true;
        sessionStartedAt = java.time.Instant.now().toString();
    }

    public void endSession(){
        saveCurrentSession(); // save one last time
        // then convert to past session and clear current session memory
        sessionStore.finalizeCurrentSession();
        refreshState();
    }

    private void resetCurrentOreAmounts()
    {
        currentOreAmounts.clear();
        for (int itemId : LOVAKENGJ_ORE_IDS)
        {
            currentOreAmounts.put(itemId, 0);
        }
    }


    private void loadStateFromCurrentSession(SessionSnapshot snapshot){
        this.ticksElapsed = snapshot.getTicksElapsed();
        this.dynamiteUsed = snapshot.getDynamiteUsed();
        this.excavationXPGained = snapshot.getExcavationXPGained();
        this.sessionStartedAt = snapshot.getStartedAt();

        sessionTimeValue.setText(snapshot.getSessionTimeText());
        dynamiteUsedValue.setText(INT_FMT.format(snapshot.getDynamiteUsed()));
        excavationXPValue.setText(INT_FMT.format((int) snapshot.getExcavationXPGained()));
        xpToCollectValue.setText(snapshot.getXpToCollectText());
        xpPerHourValue.setText(snapshot.getXpPerHourText());
        grossProfitValue.setText(snapshot.getGrossProfitText());
        dynamiteCostValue.setText(snapshot.getDynamiteCostText());
        netProfitValue.setText(snapshot.getNetProfitText());
        netGpPerHourValue.setText(snapshot.getNetGpPerHourText());

        resetCurrentOreAmounts();
        if (snapshot.getOreAmounts() != null)
        {
            for (int itemId : LOVAKENGJ_ORE_IDS)
            {
                currentOreAmounts.put(itemId, snapshot.getOreAmounts().getOrDefault(itemId, 0));
            }
        }
        updateCurrentOreCountPanel();

        activeSession = true;
    }


    public void resetState(){
        ticksElapsed = 0;
        dynamiteUsed = 0;
        excavationXPGained = 0;

        sessionStartedAt = null;
        activeSession = false;

        sessionTimeValue.setText("00:00:00");
        dynamiteUsedValue.setText("0");
        excavationXPValue.setText("0");
        xpToCollectValue.setText("0");
        xpPerHourValue.setText("0");
        grossProfitValue.setText("0");
        dynamiteCostValue.setText("0");
        netProfitValue.setText("0");
        netGpPerHourValue.setText("0");

        resetCurrentOreAmounts();
        updateCurrentOreCountPanel();
    }


    public void loadPreviousSessions()
    {
        previousSessions.clear();
        List<SessionSnapshot> sessions = sessionStore.loadPreviousSessions();
        previousSessions.addAll(sessions);
    }

    public void displayPreviousSessions()
    {
        pastSessionsContainer.removeAll();

        if (previousSessions.isEmpty())
        {
            JLabel empty = new JLabel("No previous sessions yet.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setForeground(Color.LIGHT_GRAY);
            pastSessionsContainer.add(empty);
        }
        else
        {

            for (SessionSnapshot snapshot : previousSessions)
            {
                pastSessionsContainer.add(buildPastSessionCard(snapshot));
                pastSessionsContainer.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }

        pastSessionsContainer.revalidate();
        pastSessionsContainer.repaint();
        revalidate();
        repaint();
    }

    public void saveCurrentSession()
    {
        SessionSnapshot snapshot = new SessionSnapshot();

        snapshot.setTicksElapsed(ticksElapsed);
        snapshot.setDynamiteUsed(dynamiteUsed);
        snapshot.setExcavationXPGained(excavationXPGained);

        snapshot.setSessionTimeText(sessionTimeValue.getText());
        snapshot.setXpToCollectText(xpToCollectValue.getText());
        snapshot.setXpPerHourText(xpPerHourValue.getText());
        snapshot.setGrossProfitText(grossProfitValue.getText());
        snapshot.setDynamiteCostText(dynamiteCostValue.getText());
        snapshot.setNetProfitText(netProfitValue.getText());
        snapshot.setNetGpPerHourText(netGpPerHourValue.getText());

        snapshot.setStartedAt(sessionStartedAt);
        snapshot.setOreAmounts(new LinkedHashMap<>(currentOreAmounts));

        // save to memory (config)
        sessionStore.saveCurrentSession(snapshot);
    }


    private Duration getElapsed()
    {
        return Duration.ofMillis(ticksElapsed * 600L);
    }

    private static String formatDuration(Duration d)
    {
        long seconds = d.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private double[] getOreTotals(Map<Integer, Map<String, Integer>> oreData)
    {
        double totalXP = 0;
        double totalGP = 0;
        for (Map.Entry<Integer, Map<String, Integer>> entry : oreData.entrySet())
        {
            Map<String, Integer> data = entry.getValue();
            int amount = data.get("amount");
            int xp = data.get("xp");
            int price = data.get("price");

            totalXP += (double) amount * xp;
            totalGP += (double) amount * price;
        }

        double bonusScaling = prospectorBonus();
        totalXP = totalXP * bonusScaling;

        return new double[]{totalXP, totalGP};
    }

    private int getDynamitePrice()
    {
        return itemManager.getItemPrice(ItemID.LOVAKENGJ_DYNAMITE_FUSED);
    }

    public void updateData(Map<Integer, Map<String, Integer>> oreData)
    {

        if (!activeSession){
            return;
        }

        ticksElapsed += 1;

        Duration elapsedTime = getElapsed();
        String elapsedTimeFormatted = formatDuration(elapsedTime);

        sessionTimeValue.setText(elapsedTimeFormatted);
        dynamiteUsedValue.setText(INT_FMT.format(dynamiteUsed));

        double[] oreTotals = getOreTotals(oreData);
        double oreXP = oreTotals[0];
        xpToCollectValue.setText(INT_FMT.format(oreXP));

        double elapsedHours = elapsedTime.toMillis() / 3_600_000.0;
        excavationXPValue.setText(INT_FMT.format((int) excavationXPGained));

        double totalXP = excavationXPGained + oreXP;
        int xpPerHour = elapsedTime.isZero() ? 0: (int) Math.round(totalXP / elapsedHours);
        xpPerHourValue.setText(INT_FMT.format(xpPerHour));

        int oreGP = (int) oreTotals[1];
        grossProfitValue.setText(INT_FMT.format(oreGP));

        int dynamiteCost = dynamiteUsed * getDynamitePrice();
        dynamiteCostValue.setText(INT_FMT.format(dynamiteCost));

        int netGP = oreGP - dynamiteCost;
        netProfitValue.setText(INT_FMT.format(netGP));

        int gpPerHour = elapsedTime.isZero() ? 0: (int) Math.round(netGP / elapsedHours);
        netGpPerHourValue.setText(INT_FMT.format(gpPerHour));

        resetCurrentOreAmounts();

        for (Map.Entry<Integer, Map<String, Integer>> entry : oreData.entrySet())
        {
            Map<String, Integer> data = entry.getValue();
            int itemId = data.get("itemId");
            int amount = data.get("amount");
            currentOreAmounts.put(itemId, amount);
        }

        updateCurrentOreCountPanel();
        saveCurrentSession();
    }

    public void excavation(int excavationXP){
        if (!activeSession){
            startSession();
        }

        double bonusScaling = prospectorBonus();
        excavationXPGained += excavationXP * bonusScaling;
    }

    public void incrementDynamiteUsed(){
        dynamiteUsed += 1;
    }

    public boolean isSessionActive(){
        return activeSession;
    }

    private double prospectorBonus()
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null)
        {
            return 1.0;
        }

        Item[] items = equipment.getItems();

        int headSlot = items[EquipmentInventorySlot.HEAD.getSlotIdx()].getId();
        boolean prospectorHead = headSlot == ItemID.MOTHERLODE_REWARD_HAT || headSlot == ItemID.MOTHERLODE_REWARD_HAT_GOLD;

        int bodySlot = items[EquipmentInventorySlot.BODY.getSlotIdx()].getId();
        boolean prospectorTop = bodySlot == ItemID.MOTHERLODE_REWARD_TOP || bodySlot == ItemID.MOTHERLODE_REWARD_TOP_GOLD;

        int legsSlot = items[EquipmentInventorySlot.LEGS.getSlotIdx()].getId();
        boolean prospectorLegs = legsSlot == ItemID.MOTHERLODE_REWARD_LEGS || legsSlot == ItemID.MOTHERLODE_REWARD_LEGS_GOLD;

        int bootsSlot = items[EquipmentInventorySlot.BOOTS.getSlotIdx()].getId();
        boolean prospectorBoots = bootsSlot == ItemID.MOTHERLODE_REWARD_BOOTS || bootsSlot == ItemID.MOTHERLODE_REWARD_BOOTS_GOLD;

        double xpBoost = 0.0;
        if (prospectorHead){
            xpBoost += 0.4;
        }
        if (prospectorTop){
            xpBoost += 0.8;
        }
        if (prospectorLegs){
            xpBoost += 0.6;
        }
        if (prospectorBoots){
            xpBoost += 0.2;
        }
        if (prospectorHead && prospectorTop && prospectorLegs && prospectorBoots){
            xpBoost += 0.5; // full set bonus
        }

        xpBoost = xpBoost / 100.0;
        return 1.0 + xpBoost;
    }
}
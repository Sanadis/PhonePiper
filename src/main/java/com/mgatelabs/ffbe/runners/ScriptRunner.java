package com.mgatelabs.ffbe.runners;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mgatelabs.ffbe.shared.details.*;
import com.mgatelabs.ffbe.shared.helper.DeviceHelper;
import com.mgatelabs.ffbe.shared.helper.InfoTransfer;
import com.mgatelabs.ffbe.shared.helper.MapTransfer;
import com.mgatelabs.ffbe.shared.helper.PointTransfer;
import com.mgatelabs.ffbe.shared.image.*;
import com.mgatelabs.ffbe.shared.util.AdbShell;
import com.mgatelabs.ffbe.shared.util.AdbUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by @mgatelabs (Michael Fuller) on 9/4/2017.
 */
public class ScriptRunner {

    public enum Status {
        INIT,
        READY,
        RUNNING,
        PAUSED
    }

    private PlayerDefinition playerDefinition;
    private ScriptDefinition scriptDefinition;
    private DeviceDefinition deviceDefinition;
    private ViewDefinition viewDefinition;

    private ComponentDefinition energyBar;

    private Map<String, ScreenDefinition> screens;
    private Map<String, ComponentDefinition> components;

    private Map<String, StateTransfer> transferStateMap;
    private MapTransfer transferMap;

    private Stack<String> stack;

    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private DeviceHelper deviceHelper;

    private Set<String> validScreenIds;

    private AdbShell shell;

    private Map<String, Integer> vars;

    private volatile Status status;

    private Date lastImageDate;
    private float lastImageDuration;

    public ScriptRunner(PlayerDefinition playerDefinition, DeviceHelper deviceHelper, ScriptDefinition scriptDefinition, DeviceDefinition deviceDefinition, ViewDefinition viewDefinition) {
        this.playerDefinition = playerDefinition;
        this.scriptDefinition = scriptDefinition;
        this.deviceDefinition = deviceDefinition;
        this.viewDefinition = viewDefinition;
        stack = new Stack<>();
        vars = Maps.newHashMap();
        shell = new AdbShell();

        for (VarDefinition varDefinition : scriptDefinition.getVars()) {
            if (varDefinition.getType() == VarType.INT) {
                addVar(varDefinition.getName(), Integer.parseInt(varDefinition.getValue()));
            }
        }

        screens = Maps.newHashMap();
        for (ScreenDefinition screenDefinition : viewDefinition.getScreens()) {
            screens.put(screenDefinition.getScreenId(), screenDefinition);
        }

        components = Maps.newHashMap();
        for (ComponentDefinition componentDefinition : viewDefinition.getComponents()) {
            components.put(componentDefinition.getComponentId(), componentDefinition);
        }

        energyBar = components.get("menu-energy_bar");
        if (energyBar == null) {
            throw new RuntimeException("Cannot find required component id: " + "menu-energy_bar");
        }

        for (Map.Entry<String, StateDefinition> entry : scriptDefinition.getStates().entrySet()) {
            entry.getValue().setId(entry.getKey());
        }

        System.out.println("------------");
        System.out.println("Generating State Info : " + getDateString());
        System.out.println("------------");
        System.out.println();

        transferStateMap = Maps.newHashMap();
        transferStateMap.putAll(generateStateInfo());

        transferMap = new MapTransfer();
        ComponentDefinition miniMapArea = components.get("dungeon-mini_map-area");
        ComponentDefinition miniMapAreaCenter = components.get("dungeon-mini_map-center");
        transferMap.setup(deviceDefinition.getWidth(), 12, 4, miniMapArea.getW(), miniMapArea.getH(), miniMapArea.getX(), miniMapArea.getY(), miniMapAreaCenter.getW(), miniMapAreaCenter.getH());

        validScreenIds = null;

        this.deviceHelper = deviceHelper;

        status = Status.INIT;
    }

    public Date getLastImageDate() {
        return lastImageDate;
    }

    public float getLastImageDuration() {
        return lastImageDuration;
    }

    public boolean initHelper() {
        if (deviceHelper.ready()) {
            InfoTransfer infoTransfer = new InfoTransfer();
            infoTransfer.setStates(transferStateMap);
            infoTransfer.setMap(transferMap);
            if (deviceHelper.setup(infoTransfer)) {
                return true;
            }
        }
        return false;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    private int getVar(String name) {
        return vars.getOrDefault(name, 0);
    }

    private void addVar(String name, int value) {
        vars.put(name, getVar(name) + value);
    }

    private void setVar(String name, int value) {
        vars.put(name, value);
    }

    public static String getDateString() {
        return sdf.format(new Date());
    }

    private static Comparator<PointTransfer> pointTransferComparator = new Comparator<PointTransfer>() {
        @Override
        public int compare(PointTransfer o1, PointTransfer o2) {
            final int c = Integer.compare(o1.getOffset(), o2.getOffset());
            return c == 0 ? Integer.compare(o1.getIndex(), o2.getIndex()) : c;
        }
    };

    private Map<String, StateTransfer> generateStateInfo() {

        Map<String, StateTransfer> results = Maps.newHashMap();

        for (Map.Entry<String, StateDefinition> stateEntry : scriptDefinition.getStates().entrySet()) {

            StateTransfer stateTransfer = new StateTransfer();
            stateTransfer.setStateId(stateEntry.getKey());
            stateTransfer.setScreenIds(stateEntry.getValue().determineScreenIds());

            // Collect every point
            List<PointTransfer> points = Lists.newArrayList();
            for (int i = 0; i < stateTransfer.getScreenIds().size(); i++) {
                ScreenDefinition screenDefinition = screens.get(stateTransfer.getScreenIds().get(i));
                for (SamplePoint point : screenDefinition.getPoints()) {
                    points.add(new PointTransfer(RawImageWrapper.getOffsetFor(deviceDefinition.getWidth(), 12, point.getX(), point.getY(), RawImageWrapper.ImageFormats.RGBA), (byte) i, (byte) point.getR(), (byte) point.getG(), (byte) point.getB()));
                }
            }
            Collections.sort(points, pointTransferComparator);
            // Sort them
            stateTransfer.setPoints(points);
            int jumpOffset = 0;
            int readOffset = 0;
            for (PointTransfer transfer : points) {
                int requestedOffset = transfer.getOffset();
                if (requestedOffset == jumpOffset) {
                    transfer.setOffset(0);
                } else {
                    jumpOffset = requestedOffset;
                    transfer.setOffset(jumpOffset - readOffset);
                    readOffset = jumpOffset + 3; // Read ahead for 3 bytes
                }
            }

            results.put(stateTransfer.getStateId(), stateTransfer);
        }

        return results;
    }

    public boolean isRunning() {
        return this.status == Status.RUNNING;
    }

    private String currentStateId;

    public String getCurrentStateId() {
        return currentStateId;
    }

    public Set<String> getValidScreenIds() {
        return ImmutableSet.copyOf(validScreenIds);
    }

    public void run(String stateName) {
        currentStateId = stateName;
        this.status = Status.RUNNING;

        ImageWrapper imageWrapper;

        StateDefinition stateDefinition = scriptDefinition.getStates().get(stateName);

        if (stateDefinition == null) {
            throw new RuntimeException("Cannot find state with id: " + stateName);
        }

        while (isRunning()) {

            if (!shell.isReady()) {
                System.out.println("Bad Shell: Starting again");
                try {
                    shell.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    shell = new AdbShell();
                    wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            if (deviceHelper != null && deviceHelper.getFailures() > 20) {
                deviceHelper = null;
            }

            if (deviceHelper != null) {
                long startTime = System.nanoTime();
                AdbUtils.persistScreen(shell);
                long endTime = System.nanoTime();

                long dif = endTime - startTime;

                lastImageDate = new Date();
                lastImageDuration = ((float) dif / 1000000000.0f);

                String seconds = String.format("%2.2f", lastImageDuration);

                System.out.println("------------");
                System.out.println("Image: Persisted " + seconds + "s" + " : " + getDateString());
                System.out.println("------------");
                System.out.println();

                imageWrapper = null;
            } else {

                long startTime = System.nanoTime();
                imageWrapper = AdbUtils.getScreen();
                long endTime = System.nanoTime();

                long dif = endTime - startTime;

                lastImageDate = new Date();
                lastImageDuration = ((float) dif / 1000000000.0f);

                String seconds = String.format("%2.2f", lastImageDuration);

                if (imageWrapper == null || !imageWrapper.isReady()) {
                    System.out.println("------------");
                    System.out.println("Image: Failure: " + seconds + "s" + " : " + getDateString());
                    System.out.println("------------");
                    System.out.println();
                    waitFor(250);
                    continue;
                } else {
                    System.out.println("------------");
                    System.out.println("Image: Success " + seconds + "s" + " : " + getDateString());
                    System.out.println("------------");
                    System.out.println();
                }
            }

            boolean keepRunning = true;

            while (keepRunning && isRunning()) {
                keepRunning = false;

                if (deviceHelper != null) {

                    System.out.println("------------");
                    System.out.println("Helper: /check/" + stateDefinition.getId() + " : " + getDateString());
                    System.out.println("------------");
                    System.out.println();

                    validScreenIds = deviceHelper.check(stateDefinition.getId());
                }

                StateResult result = state(stateDefinition, imageWrapper);

                switch (result.getType()) {
                    case STOP: {
                        return;
                    }
                    case POP:
                        if (stack.size() > 0) {
                            final String oldState = stack.pop();
                            stateDefinition = scriptDefinition.getStates().get(oldState);
                            if (stateDefinition == null) {
                                throw new RuntimeException("Cannot find state with id: " + oldState);
                            }
                            keepRunning = false;
                        } else {
                            throw new RuntimeException("Stack is empty");
                        }
                        break;
                    case MOVE: {
                        stateDefinition = scriptDefinition.getStates().get(result.getValue());
                        if (stateDefinition == null) {
                            throw new RuntimeException("Cannot find state with id: " + result.getValue());
                        }
                        currentStateId = stateDefinition.getId();
                        keepRunning = false;
                    }
                    break;
                    case PUSH: {
                        stack.push(stateDefinition.getName());
                        stateDefinition = scriptDefinition.getStates().get(result.getValue());
                        if (stateDefinition == null) {
                            throw new RuntimeException("Cannot find state with id: " + result.getValue());
                        }
                        currentStateId = stateDefinition.getId();
                        keepRunning = false;
                    }
                    break;
                    case SWAP: {
                        stateDefinition = scriptDefinition.getStates().get(result.getValue());
                        if (stateDefinition == null) {
                            throw new RuntimeException("Cannot find state with id: " + result.getValue());
                        }
                        currentStateId = stateDefinition.getId();
                        keepRunning = true;
                    }
                    break;
                    case REPEAT: {
                        keepRunning = false;
                    }
                    break;
                }

            }

            waitFor(1000);
        }

        System.out.println("------------");
        System.out.println("Script Stopped : " + getDateString());
        System.out.println("------------");
        System.out.println();
    }

    private StateResult state(final StateDefinition stateDefinition, final ImageWrapper imageWrapper) {

        System.out.println("------------");
        System.out.println("Running State: " + stateDefinition.getName() + " : " + getDateString());
        System.out.println("------------");
        System.out.println();

        boolean batchCmds = false;

        for (StatementDefinition statementDefinition : stateDefinition.getStatements()) {
            if (check(statementDefinition.getCondition(), imageWrapper)) {
                for (ActionDefinition actionDefinition : statementDefinition.getActions()) {
                    switch (actionDefinition.getType()) {
                        case MSG: {
                            System.out.println("------------");
                            System.out.println("MSG: " + actionDefinition.getValue() + " : " + getDateString());
                            System.out.println("------------");
                            System.out.println();
                        }
                        break;
                        case BATCH: {
                            if ("START".equalsIgnoreCase(actionDefinition.getValue())) {
                                batchCmds = true;
                            } else if (batchCmds) {
                                batchCmds = false;
                                shell.exec();
                            }
                        }
                        break;
                        case SET: {
                            String varName = actionDefinition.getVar();
                            int value = Integer.parseInt(actionDefinition.getValue());
                            setVar(varName, value);
                        }
                        break;
                        case ADD: {
                            String varName = actionDefinition.getVar();
                            int value = Integer.parseInt(actionDefinition.getValue());
                            addVar(varName, value);
                        }
                        break;
                        case TAP:
                        case SWIPE_DOWN:
                        case SWIPE_UP:
                        case SWIPE_LEFT:
                        case SWIPE_RIGHT: {
                            ComponentDefinition componentDefinition = components.get(actionDefinition.getValue());
                            if (componentDefinition == null) {
                                throw new RuntimeException("Cannot find component with id: " + actionDefinition.getValue());
                            }
                            AdbUtils.component(deviceDefinition, componentDefinition, actionDefinition.getType(), shell, batchCmds);
                        }
                        break;
                        case WAIT: {
                            long time = Long.parseLong(actionDefinition.getValue());
                            if (time > 0) {
                                waitFor(time);
                            } else {
                                throw new RuntimeException("Invalid wait time: " + actionDefinition.getValue());
                            }
                        }
                        break;
                        case POP: {
                            return StateResult.POP;
                        }
                        case PUSH: {
                            return StateResult.push(actionDefinition.getValue());
                        }
                        case SWAP: {
                            return StateResult.swap(actionDefinition.getValue());
                        }
                        case MOVE: {
                            return StateResult.move(actionDefinition.getValue());
                        }
                        case REPEAT: {
                            return StateResult.REPEAT;
                        }
                    }
                }
            }
        }

        return StateResult.REPEAT;
    }

    private void waitFor(long milli) {
        try {
            Thread.sleep(milli);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean check(final ConditionDefinition conditionDefinition, ImageWrapper imageWrapper) {

        boolean result = false;
        boolean checkAnd = true;

        switch (conditionDefinition.getIs()) {
            case BOOLEAN: {
                result = "true".equalsIgnoreCase(conditionDefinition.getValue());
            }
            break;
            case GREATER:
            case LESS:
            case EQUAL: {
                int value = Integer.parseInt(conditionDefinition.getValue());
                String varName = conditionDefinition.getVar();
                int currentValue = getVar(varName);
                switch (conditionDefinition.getIs()) {
                    case GREATER: {
                        result = currentValue > value;
                    }
                    break;
                    case LESS: {
                        result = currentValue < value;
                    }
                    break;
                    case EQUAL: {
                        result = currentValue == value;
                    }
                    break;
                }
            }
            break;
            case SCREEN: {
                if (deviceHelper != null) {
                    result = validScreenIds.contains(conditionDefinition.getValue());
                } else {
                    ScreenDefinition screenDefinition = screens.get(conditionDefinition.getValue());
                    if (screenDefinition == null) {
                        throw new RuntimeException("Cannot find screen with id: " + conditionDefinition.getValue());
                    }
                    result = SamplePoint.validate(screenDefinition.getPoints(), imageWrapper, false);
                }
            }
            break;
            case ENERGY: {
                int energy = Integer.parseInt(conditionDefinition.getValue());
                if (energy >= PlayerDefinition.MIN_ENERGY && energy <= PlayerDefinition.MAX_ENERGY) {
                    float requiredPercent = ((float) energy / (float) playerDefinition.getTotalEnergy());
                    int requiredPixel = ((int) (energyBar.getW() * requiredPercent) + 1);
                    if (requiredPixel > energyBar.getW()) {
                        requiredPixel = energyBar.getW();
                    }
                    Sampler sample = new Sampler();
                    if (deviceHelper != null) {
                        int[] pixels = deviceHelper.pixel(RawImageWrapper.getOffsetFor(deviceDefinition.getWidth(), 12, energyBar.getX() + requiredPixel, energyBar.getY(), RawImageWrapper.ImageFormats.RGBA));
                        if (pixels != null) {
                            sample.setR(pixels[0]);
                            sample.setG(pixels[1]);
                            sample.setB(pixels[2]);
                        } else {

                        }
                    } else {
                        imageWrapper.getPixel(energyBar.getX() + requiredPixel, energyBar.getY(), sample);
                    }
                    result = sample.getB() > 100;
                } else {
                    throw new RuntimeException("Invalid energy value: " + conditionDefinition.getValue());
                }
            }
            break;
            case NOT: {
                if (conditionDefinition.getAnd() == null) {
                    throw new RuntimeException("is (NOT) requires a AND value");
                }
                checkAnd = false;
                result = !check(conditionDefinition.getAnd(), imageWrapper);
            }
            break;
        }

        // If we have a AND handle it
        if (result && checkAnd && conditionDefinition.getAnd() != null) {
            result = check(conditionDefinition.getAnd(), imageWrapper);
        }

        // If we failed, but have a OR, check the OR
        if (!result && conditionDefinition.getOr() != null) {
            result = check(conditionDefinition.getOr(), imageWrapper);
        }

        return result;
    }
}
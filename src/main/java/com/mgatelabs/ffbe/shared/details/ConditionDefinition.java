package com.mgatelabs.ffbe.shared.details;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Created by @mgatelabs (Michael Fuller) on 9/3/2017.
 */
public class ConditionDefinition {
    private ConditionType is;
    private String value;
    private String var;

    private ConditionDefinition and;
    private ConditionDefinition or;

    public ConditionType getIs() {
        return is;
    }

    public void setIs(ConditionType is) {
        this.is = is;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public ConditionDefinition getAnd() {
        return and;
    }

    public void setAnd(ConditionDefinition and) {
        this.and = and;
    }

    public ConditionDefinition getOr() {
        return or;
    }

    public void setOr(ConditionDefinition or) {
        this.or = or;
    }

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public Set<String> determineScreenIds() {
        Set<String> screenIds = Sets.newHashSet();

        if (is == ConditionType.SCREEN) {
            screenIds.add(value);
        }

        if (and != null) {
            screenIds.addAll(and.determineScreenIds());
        }

        if (or != null) {
            screenIds.addAll(or.determineScreenIds());
        }

        return screenIds;
    }
}
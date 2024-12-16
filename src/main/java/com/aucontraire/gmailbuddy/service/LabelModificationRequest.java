package com.aucontraire.gmailbuddy.service;

import java.util.List;

public class LabelModificationRequest {
    private List<String> labelsToAdd;
    private List<String> labelsToRemove;

    public List<String> getLabelsToAdd() {
        return labelsToAdd;
    }

    public void setLabelsToAdd(List<String> labelsToAdd) {
        this.labelsToAdd = labelsToAdd;
    }

    public List<String> getLabelsToRemove() {
        return labelsToRemove;
    }

    public void setLabelsToRemove(List<String> labelsToRemove) {
        this.labelsToRemove = labelsToRemove;
    }
}

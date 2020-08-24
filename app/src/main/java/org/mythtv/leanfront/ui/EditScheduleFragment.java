/*
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.SparseArray;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.RecordRule;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class EditScheduleFragment extends GuidedStepSupportFragment {

    private RecordRule mProgDetails;
    private RecordRule mRecordRule;
    private ArrayList<XmlNode> mDetailsList;
    private ArrayList<RecordRule> mTemplateList = new ArrayList<>();
    private ArrayList<String> mPlayGroupList;
    private ArrayList<String> mRecGroupList;
    private ArrayList<String> mRecStorageGroupList;
    private SparseArray<String> mInputList = new SparseArray<>();
    private ArrayList<String> mRecRuleFilterList;
    private int mGroupId;
    private ArrayList<ActionGroup> mGroupList = new ArrayList<>();
    private String mNewValueText;

    private static DateFormat timeFormatter;
    private static DateFormat dateFormatter;
    private static DateFormat dayFormatter;

    private static final int ACTIONTYPE_RADIOBNS = 1;
    private static final int ACTIONTYPE_CHECKBOXES = 2;
    private static final int ACTIONTYPE_NUMERIC = 3;
    private static final int ACTIONTYPE_NUMERIC_UNSIGNED = 4;
    private static final int ACTIONTYPE_BOOLEAN = 5;


    public EditScheduleFragment(ArrayList<XmlNode> detailsList) {

        /*
            Details are in this order
                Video.ACTION_GETPROGRAMDETAILS,
                Video.ACTION_GETRECORDSCHEDULELIST,
                Video.ACTION_GETPLAYGROUPLIST,
                Video.ACTION_GETRECGROUPLIST,
                Video.ACTION_GETRECSTORAGEGROUPLIST,
                Video.ACTION_GETINPUTLIST
                Video.ACTION_GETRECRULEFILTERLIST
         */
        mDetailsList = detailsList;
    }

    private void setupData() {
        RecordRule defaultTemplate = null;
        XmlNode progDetailsNode = mDetailsList.get(0); // ACTION_GETPROGRAMDETAILS
        if (progDetailsNode != null)
            mProgDetails = new RecordRule().fromProgram(progDetailsNode);
        XmlNode recRulesNode = mDetailsList.get(1) // ACTION_GETRECORDSCHEDULELIST
                .getNode("RecRules");
        if (recRulesNode != null) {
            XmlNode recRuleNode = recRulesNode.getNode("RecRule");
            while (recRuleNode != null) {
                int id = Integer.parseInt(recRuleNode.getString("Id"));
                if (id == mProgDetails.recordId)
                    mRecordRule = new RecordRule().fromSchedule(recRuleNode);
                String type = recRuleNode.getString("Type");
                if ("Recording Template".equals(type)) {
                    RecordRule template = new RecordRule().fromSchedule(recRuleNode);
                    mTemplateList.add(template);
                    if ("Default (Template)".equals(template.title))
                        defaultTemplate = template;
                }
                recRuleNode = recRuleNode.getNextSibling();
            }
        }
        if (mRecordRule == null)
            mRecordRule = new RecordRule().mergeTemplate(defaultTemplate);
        if (mProgDetails != null)
            mRecordRule.mergeProgram(mProgDetails);
        if (mRecordRule.type == null)
            mRecordRule.type = "Not Recording";

        // Lists
        mPlayGroupList = getStringList(mDetailsList.get(2)); // ACTION_GETPLAYGROUPLIST
        mRecGroupList = getStringList(mDetailsList.get(3)); // ACTION_GETRECGROUPLIST
        mRecStorageGroupList = getStringList(mDetailsList.get(4)); // ACTION_GETRECSTORAGEGROUPLIST

        mInputList.put(0, getContext().getString(R.string.sched_input_any));
        XmlNode inputListNode = mDetailsList.get(5); // ACTION_GETINPUTLIST
        if (inputListNode != null) {
            XmlNode inputsNode = inputListNode.getNode("Inputs");
            if (inputsNode != null) {
                XmlNode inputNode = inputsNode.getNode("Input");
                while (inputNode != null) {
                    int id = inputNode.getNode("Id").getInt(-1);
                    String displayName = inputNode.getString("DisplayName");
                    if (id > 0)
                        mInputList.put(id, displayName);
                    inputNode = inputNode.getNextSibling();
                }
            }
        }
        mRecRuleFilterList = new ArrayList<>();
        XmlNode filterListNode = mDetailsList.get(6); // ACTION_GETRECRULEFILTERLIST
        if (filterListNode != null) {
            XmlNode filtersNode = filterListNode.getNode("RecRuleFilters");
            if (filtersNode != null) {
                XmlNode filterNode = filtersNode.getNode("RecRuleFilter");
                while (filterNode != null) {
                    int id = filterNode.getNode("Id").getInt(-1);
                    String description = filterNode.getString("Description");
                    for (int ix = mRecRuleFilterList.size(); ix <= id; ix++)
                        mRecRuleFilterList.add(null);
                    if (id >= 0)
                        mRecRuleFilterList.set(id, description);
                    filterNode = filterNode.getNextSibling();
                }
            }
        }
    }


    public static ArrayList<String> getStringList(XmlNode listNode) {
        ArrayList<String> ret = new ArrayList<>();
        ret.add("Default");
        if (listNode != null) {
            XmlNode stringNode = listNode.getNode("String");
            while (stringNode != null) {
                String value = stringNode.getString();
                if (!"Default".equals(value))
                    ret.add(value);
                stringNode = stringNode.getNextSibling();
            }
        }
        return ret;
    }


    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        if (timeFormatter == null) {
            timeFormatter = android.text.format.DateFormat.getTimeFormat(getContext());
            dateFormatter = android.text.format.DateFormat.getLongDateFormat(getContext());
            dayFormatter = new SimpleDateFormat("EEE ");
        }
        if (mRecordRule == null)
            setupData();
        Activity activity = getActivity();
        String title = mRecordRule.title;
        StringBuilder dateTime = new StringBuilder();
        dateTime.append(mRecordRule.station).append(' ')
                .append(dayFormatter.format(mRecordRule.startTime))
                .append(dateFormatter.format(mRecordRule.startTime)).append(' ')
                .append(timeFormatter.format(mRecordRule.startTime));
        StringBuilder details = new StringBuilder();
        String subTitle = mRecordRule.subtitle;
        if (subTitle != null)
            details.append(subTitle).append("\n");
        String desc = mRecordRule.description;
        if (desc != null)
            details.append(desc);
        Drawable icon = activity.getDrawable(R.drawable.ic_voicemail);
        return new GuidanceStylist.Guidance(title, details.toString(), dateTime.toString(), icon);
    }

    static final int[] sTypePrompts = {
            R.string.sched_type_not, R.string.sched_type_this,
            R.string.sched_type_one, R.string.sched_type_all};
    static final String[] sTypeValues = {
            "Not Recording", "Single Record",
            "Record One", "Record All"};
    static final int[] sRepeatPrompts = {
            R.string.sched_new_and_repeat, R.string.sched_new_only};
    static final int[] sActivePrompts = {
            R.string.sched_inactive_msg, R.string.sched_active_msg};
    static final int [] sDupMethodPrompts = {
            R.string.sched_dup_none, R.string.sched_dup_s_and_d,
            R.string.sched_dup_s_then_d, R.string.sched_dup_s,
            R.string.sched_dup_d };
    static final String [] sDupMethodValues = {
            "None", "Subtitle and Description",
            "Subtitle then Description", "Subtitle",
            "Description" };
    static final int [] sDupScopePrompts = {
            R.string.sched_dup_both, R.string.sched_dup_curr,
            R.string.sched_dup_prev };
    static final String [] sDupScopeValues = {
            "All Recordings", "Current Recordings",
            "Previous Recordings" };
    static final int [] sRecProfilePrompts = {
            R.string.sched_recprof_default, R.string.sched_recprof_livetv,
            R.string.sched_recprof_highq, R.string.sched_recprof_lowq };
    static final String [] sRecProfileValues = {
            "Default", "Live TV",
            "High Quality", "Low Quality" };
    static final int [] sNewestPrompts = {
            R.string.sched_max_dont,
            R.string.sched_max_delete};
    static final int [] sAutoExpirePrompts = {
            R.string.sched_auto_expire_off, R.string.sched_auto_expire_on};
    static final int [] sPostProcPrompts = {
            R.string.sched_pp_commflag, R.string.sched_pp_metadata,
            R.string.sched_pp_transcode,
            R.string.sched_pp_job1, R.string.sched_pp_job2,
            R.string.sched_pp_job3, R.string.sched_pp_job4};
    static final int [] sMetaDataPrompts = {
            R.string.sched_metadata_type_tv, R.string.sched_metadata_type_movie };

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> mainActions, Bundle savedInstanceState) {
        if (mRecordRule == null)
            setupData();

        int ix;

        ActionGroup group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_type,
                sTypePrompts, sTypeValues, mRecordRule.type, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Recording Group
        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_rec_group,
                null, mRecGroupList.toArray(new String[mRecGroupList.size()+1]),
                mRecordRule.recGroup, true);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Active
        group = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_active,
                sActivePrompts, ! mRecordRule.inactive);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_play_group,
                null, mPlayGroupList.toArray(new String[mPlayGroupList.size()]),
                mRecordRule.playGroup, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        group = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_start_offset,
                mRecordRule.startOffset);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        group = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_end_offset,
                mRecordRule.endOffset);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        group = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_repeats,
                sRepeatPrompts, mRecordRule.newEpisOnly);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        group = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_priority,
                mRecordRule.recPriority);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // inputs - intvalues
        String [] stringPrompts = new String[mInputList.size()];
        int [] intValues = new int[mInputList.size()];
        for (ix = 0 ; ix < mInputList.size(); ix++) {
            stringPrompts[ix] = mInputList.valueAt(ix);
            intValues[ix] = mInputList.keyAt(ix);
        }

        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_input,
                 stringPrompts, intValues, mRecordRule.recPriority);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Duplicate Match Method
        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_dupmethod,
                sDupMethodPrompts, sDupMethodValues, mRecordRule.dupMethod, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Dup Scope
        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_dupscope,
                sDupScopePrompts, sDupScopeValues, mRecordRule.dupIn, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Filters
        stringPrompts = new String[mRecRuleFilterList.size()];
        stringPrompts = mRecRuleFilterList.toArray(stringPrompts);
        group = new ActionGroup(ACTIONTYPE_CHECKBOXES, R.string.sched_filters,
                null, stringPrompts, mRecordRule.filter);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Recording profile
        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_rec_profile,
                sRecProfilePrompts, sRecProfileValues, mRecordRule.recProfile, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Storage Group
        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_storage_grp,
                null, mRecStorageGroupList.toArray(new String[mRecStorageGroupList.size()]),
                mRecordRule.storageGroup, false);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Max to keep
        group = new ActionGroup(ACTIONTYPE_NUMERIC_UNSIGNED, R.string.sched_max_to_keep,
                mRecordRule.maxEpisodes);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Max Newest
        group = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_max_newest,
                sNewestPrompts, mRecordRule.maxNewest);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Auto Expire
        group = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_auto_expire,
                sAutoExpirePrompts, mRecordRule.autoExpire);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // post Processing
        int ppVal = 0;
        ix = 0;
        ppVal |= mRecordRule.autoCommflag   ? 1 << 0 : 0;
        ppVal |= mRecordRule.autoMetaLookup ? 1 << 1 : 0;
        ppVal |= mRecordRule.autoTranscode  ? 1 << 2 : 0;
        ppVal |= mRecordRule.autoUserJob1   ? 1 << 3 : 0;
        ppVal |= mRecordRule.autoUserJob2   ? 1 << 4 : 0;
        ppVal |= mRecordRule.autoUserJob3   ? 1 << 5 : 0;
        ppVal |= mRecordRule.autoUserJob4   ? 1 << 6 : 0;

        group = new ActionGroup(ACTIONTYPE_CHECKBOXES, R.string.sched_pp_title,
                sPostProcPrompts, (String[])null, ppVal);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Metadata type ttvdb.py_ or tmdb3.py_
        boolean mdVal = mRecordRule.inetref != null && mRecordRule.inetref.startsWith("tmdb3.py_");

        group = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_metadata_type,
                sMetaDataPrompts, mdVal);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Metadata number
        int id = 0;
        if (mRecordRule.inetref != null) {
            String [] parts = mRecordRule.inetref.split("_");
            if (parts.length == 2) {
                try {
                    id = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    id = 0;
                }
            }
        }
        group = new ActionGroup(ACTIONTYPE_NUMERIC_UNSIGNED, R.string.sched_metadata_id,
                id);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);

        // Use Template
        stringPrompts = new String[mTemplateList.size()];
        intValues = new int[mTemplateList.size()];
        for (ix = 0 ; ix < mTemplateList.size(); ix++) {
            stringPrompts[ix] = mTemplateList.get(ix).title;
            if (stringPrompts[ix].endsWith(" (Template)"))
                stringPrompts[ix] = stringPrompts[ix].substring(0, stringPrompts[ix].length() - 11);
            intValues[ix] = ix;
        }

        group = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_use_template,
                stringPrompts, intValues, -1);
        mainActions.add(group.mGuidedAction);
        mGroupList.add(group);



    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        return mGroupList.get(group).onSubGuidedActionClicked(action);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        mGroupList.get(group).onGuidedActionEditedAndProceed(action);
        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        mGroupList.get(group).onGuidedActionClicked(action);
        super.onGuidedActionClicked(action);
    }

    private void promptForNewValue(GuidedAction action, String initValue) {
        mNewValueText = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.sched_new_entry);
        EditText input = new EditText(getContext());
        input.setText(initValue);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mNewValueText = input.getText().toString();
                onSubGuidedActionClicked(action);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }



    // Handles groups of checkboxes or radiobuttons.
    private class ActionGroup {
        int mActionType;
        int mTitle;
        int[] mPrompts;          // can be null in which case mStringValues are used
        int[] mIntValues;        // can be null
        String[] mStringValues;  // can be null
        int mId;                  // id of main action. sub actions have sequential ids after this.
        int mSubActionCount;
        int mIntResult = -1;
        String mStringResult;
        boolean mEditLast;
        int mSelectedPrompt = -1;
        GuidedAction mGuidedAction;

        ActionGroup(int actionType, int title, int[] prompts, int[] intValues,
                    String[] stringValues, String currStringValue, int currIntValue, boolean editLast) {
            mActionType = actionType;
            mIntValues = intValues;
            mStringValues = stringValues;
            mPrompts = prompts;
            mIntResult = currIntValue;
            mStringResult = currStringValue;
            if (mStringValues != null)
                mEditLast = editLast;
            mId = mGroupId++ * 100 + 1;
            int subId = mId;

            if (intValues != null)
                mSubActionCount = intValues.length;
            else if (stringValues != null)
                mSubActionCount = stringValues.length;
            else if (mPrompts != null && actionType != ACTIONTYPE_BOOLEAN)
                mSubActionCount = mPrompts.length;
            else
                mSubActionCount = 0;

            List<GuidedAction> subActions = new ArrayList<>();
            for (int ix = 0; ix < mSubActionCount; ix++) {
                GuidedAction.Builder builder = new GuidedAction.Builder(getActivity())
                        .id(++subId);
                if (mEditLast && ix == mSubActionCount-1) {
//                    builder.descriptionEditable(true); // editable actions cannot be checked
                    builder.title(R.string.sched_new_entry);
                }
                else if (mPrompts != null)
                    builder.title(mPrompts[ix]);
                else if (mStringValues != null)
                    builder.title(mStringValues[ix]);
                boolean checked = false;
                if (mActionType == ACTIONTYPE_CHECKBOXES)
                    checked = ((mIntResult & (1 << ix)) != 0);
                else {
                    if (currStringValue != null)
                        checked = (currStringValue.equals(mStringValues[ix]));
                    else
                        checked = (currIntValue == mIntValues[ix]);
                    if (checked) {
                        if (mPrompts != null)
                            mSelectedPrompt = ix;
                        if (mStringValues != null)
                            mStringResult = mStringValues[ix];
                        if (mIntValues != null)
                            mIntResult = mIntValues[ix];
                    }
                }
                switch (mActionType) {
                    case ACTIONTYPE_CHECKBOXES:
                        builder.checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID);
                        break;
                    case ACTIONTYPE_RADIOBNS:
                        builder.checkSetId(mId);
                        break;
                }
                builder.checked(checked);
                subActions.add(builder.build());
            }
            GuidedAction.Builder builder = new GuidedAction.Builder(getActivity())
                    .id(mId)
                    .title(title);
            if (mActionType == ACTIONTYPE_RADIOBNS) {
                if (mSelectedPrompt >= 0)
                    builder.description(mPrompts[mSelectedPrompt]);
                else if (mStringResult != null)
                    builder.description(mStringResult);
            }
            else if (mActionType == ACTIONTYPE_NUMERIC
                    || mActionType == ACTIONTYPE_NUMERIC_UNSIGNED) {
                builder.description(String.valueOf(mIntResult));
                builder.descriptionEditable(true);
                int type = InputType.TYPE_CLASS_NUMBER;
                if (mActionType == ACTIONTYPE_NUMERIC)
                    type |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                builder.descriptionEditInputType (type);
            }
            else if (mActionType == ACTIONTYPE_BOOLEAN) {
                builder.checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID);
                builder.checked(mIntResult != 0);
//                builder.description(getContext().getString(mPrompts[mIntResult]));
                builder.description(mPrompts[mIntResult]);
            }
            if (mSubActionCount > 0)
                builder.subActions(subActions);
            mGuidedAction = builder.build();
        }

        /**
         * Constructor for list of integer values
         * @param actionType   ACTIONTYPE_RADIOBNS
         * @param title        title
         * @param prompts      array: string id prompt for each value.
         * @param intValues    array: Int values.
         * @param currIntValue initial value
         */
        ActionGroup(int actionType, int title, @NonNull int[] prompts, @NonNull int[] intValues,
                    int currIntValue) {
            this(actionType, title, prompts, intValues,
                    null, null, currIntValue, false);
        }

        /**
         * Constructor for one numeric input value
         * @param actionType   ACTIONTYPE_NUMERIC or ACTIONTYPE_NUMERIC_UNSIGNED
         * @param title        title
         * @param currIntValue initial value
         */
        ActionGroup(int actionType, int title, int currIntValue) {
            this(actionType, title, null, null,
                    null, null, currIntValue, false);
        }


        /**
         * Constructor for list of integer values with string prompts
         * @param actionType   ACTIONTYPE_RADIOBNS
         * @param title        title
         * @param prompts      array: string prompt for each value. Null for numeric input.
         * @param intValues    array: Int values. Null for numeric input.
         * @param currIntValue initial value
         */
        ActionGroup(int actionType, int title, @NonNull String [] prompts, @NonNull int[] intValues,
                    int currIntValue) {
            this(actionType, title, null, intValues,
                    prompts, null, currIntValue, false);
        }


        /**
         * Constructor for list of string values with string id prompts.
         * @param actionType       ACTIONTYPE_RADIOBNS
         * @param title            title
         * @param prompts          array of prompts if descriptions diff from value. null to use
         *                         values as prompts
         * @param stringValues     array of values
         * @param currStringValue  initial value
         * @param allowCreateNew   add an option to create a new value
         */
        ActionGroup(int actionType, int title, int[] prompts,
                    @NonNull  String[] stringValues, String currStringValue, boolean allowCreateNew) {
            this(actionType, title, prompts, null,
                    stringValues, currStringValue, -1, allowCreateNew);
        }

        /**
         * Constructor for check boxes with string or sting id prompts
         * @param actionType    ACTIONTYPE_CHECKBOXES
         * @param title         title
         * @param prompts       array: string id prompts. null to use string values.
         * @param stringPrompts array: String prompts. Null to use string id's.
         * @param currIntValue  initial value (bit mask)
         */
        ActionGroup(int actionType, int title, int[] prompts, String[] stringPrompts,
                    int currIntValue) {
            this(actionType, title, prompts, null,
                    stringPrompts, null, currIntValue, false);
        }

        /**
         * Constructor for boolean prompt
         * @param actionType     ACTIONTYPE_BOOLEAN
         * @param title          title
         * @param prompts        array of two entries, for false and true
         * @param currBoolValue  initial value
         */
        ActionGroup(int actionType, int title, int [] prompts, boolean currBoolValue) {
            this(actionType, title, prompts, null,
                    null, null,
                    currBoolValue ? 1 : 0, false);
        }

        public boolean onSubGuidedActionClicked(GuidedAction action) {
            int id = (int) action.getId();
            int ix = (id % 100) - 2;
//            int groupIx = id / 100;
            if (action.isChecked()) {
                if (mActionType == ACTIONTYPE_CHECKBOXES) {
                    mIntResult |= (1 << ix);
                    return false;
                }
                else if (mActionType == ACTIONTYPE_RADIOBNS) {
//                    ActionGroup group = mGroupList.get(groupIx);
                    if (mEditLast && ix == mStringValues.length-1) {
                        if (mNewValueText == null)
                            promptForNewValue(action, mStringValues[mStringValues.length - 1]);
                        else
                            mStringValues[mStringValues.length-1] = mNewValueText;
                        mNewValueText = null;
                        action.setDescription(mStringValues[mStringValues.length-1]);
                    }
                    mSelectedPrompt = ix;
                    if (mIntValues != null)
                        mIntResult = mIntValues[ix];
                    if (mStringValues != null)
                        mStringResult = mStringValues[ix];
                    if (mPrompts != null) {
                        mGuidedAction.setDescription(getContext().getString(mPrompts[ix]));
                    } else if (mStringValues != null) {
                        mGuidedAction.setDescription(mStringResult);
                    }
                    notifyActionChanged(findActionPositionById(mId));
                }
            }
            else {
                if (mActionType == ACTIONTYPE_CHECKBOXES) {
                    mIntResult &= (-1 - (1 << ix));
                    return false;
                }

            }
            return true;
        }

        public void onGuidedActionEditedAndProceed(GuidedAction action) {
            if (mActionType == ACTIONTYPE_NUMERIC
                || mActionType == ACTIONTYPE_NUMERIC_UNSIGNED) {
                try {
                    mIntResult = Integer.parseInt(action.getDescription().toString().trim());
                }
                catch(Exception e) {
                    mIntResult = 0;
                }
                action.setDescription(String.valueOf(mIntResult));
            }
        }

        public void onGuidedActionClicked(GuidedAction action) {
            if (mActionType == ACTIONTYPE_BOOLEAN) {
                mIntResult = action.isChecked() ? 1 : 0;
                action.setDescription(getContext().getString(mPrompts[mIntResult]));
                notifyActionChanged(findActionPositionById(action.getId()));
            }
        }
    }
}
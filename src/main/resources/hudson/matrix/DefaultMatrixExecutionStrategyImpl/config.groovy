package hudson.matrix.DefaultMatrixExecutionStrategyImpl;

import hudson.matrix.MatrixConfigurationSorterDescriptor
import hudson.model.Result;

def f = namespace(lib.FormTagLib)

// Execution order applies to both parallel and sequential scheduling (see DefaultMatrixExecutionStrategyImpl.run()).
// Keeping the sorter inside the sequential optional block hid it when parallel mode was selected and could drop the
// sorter on save when sequential was unchecked.
if (MatrixConfigurationSorterDescriptor.all().size()>1) {
    f.dropdownDescriptorSelector(title:_("Execution order of builds"), field:"sorter")
}

f.optionalBlock (field:"runSequentially", title:_("Run each configuration sequentially"), inline:true) {
}

f.optionalBlock (field:"hasTouchStoneCombinationFilter", title:_("Execute touchstone builds first"), inline:true) {
    // TODO: help="/help/matrix/touchstone.html">
    // TODO: move l10n from MatrixProject/configEntries.jelly

    f.entry(title:_("Filter"), field:"touchStoneCombinationFilter") {
        f.textbox()
    }

    f.entry(title:_("Required result"), field:"touchStoneResultCondition", description:_("required.result.description")) {
        div(class:"jenkins-select") {
            select(name: "touchStoneResultCondition", class:"jenkins-select__input") {
                f.option(value: "SUCCESS", selected: my?.touchStoneResultCondition == Result.SUCCESS, _("Stable"))
                f.option(value: "UNSTABLE", selected: my?.touchStoneResultCondition == Result.UNSTABLE, _("Unstable"))
            }
        }
    }
}

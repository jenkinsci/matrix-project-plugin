Behaviour.specify("DIV.labelAxis-tree", 'LabelAxis', 0, function(e) {
    var tree = new YAHOO.widget.TreeView(e);

    var i18nContainer = document.querySelector(".label-axis-i18n");
    var labels = new YAHOO.widget.TextNode(i18nContainer.getAttribute("data-i18n-labels"), tree.getRoot(), false);
    var machines = new YAHOO.widget.TextNode(i18nContainer.getAttribute("data-i18n-individual-nodes"), tree.getRoot(), false);

    var values = (e.getAttribute("values") || "").split("/");
    function has(v) {
        return values.include(v) ? 'checked="checked" ' : "";
    }

    var labelAxisDataContainer = document.querySelector(".label-axis-data-container");
    labelAxisDataContainer.childNodes.forEach(node => {
        var labelCheckbox = node.getAttribute("data-label-checkbox");

        // inserting 'checked' attribute after '<input ' hence index of 7
        var output = [labelCheckbox.slice(0, 7), has(node.getAttribute("data-label-atom")), labelCheckbox.slice(7)].join('');
        var label = node.getAttribute("data-label");
        new YAHOO.widget.HTMLNode(output, label === "machines" ? machines : labels, false);
    });

    tree.draw();
    /*
      force the rendering of HTML, so that input fields are there
      even when the form is submitted without this tree expanded.
    */
    tree.expandAll();
    tree.collapseAll();

    /*
        cancel the event.

        from http://yuilibrary.com/forum/viewtopic.php?f=89&t=8209&p=26239&hilit=HTMLNode#p26239
        "To prevent toggling and allow the link to work, add a listener to the clickEvent on that tree and simply return false"
    */
    tree.subscribe("clickEvent", function(node) {
        return false;
    });
});

Behaviour.specify(".mp-label-axis__button", "mp-label-container", 0, function(btn) {
  btn.addEventListener("click", function(evt) {
    const container = btn.closest(".mp-label-axis__container");
    if (container) {
      const labelList = container.querySelector(".mp-label-axis__list");
      if (labelList) {
        labelList.classList.toggle("jenkins-hidden");
        if (btn.dataset.hidden === "true") {
          btn.dataset.hidden = "false";
        } else {
          btn.dataset.hidden = "true";
        }
      }
    }
  });
});

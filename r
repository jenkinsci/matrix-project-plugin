# Matrix Project Plugin
In the New Item user interface, this plugin contributes the following one:
![Muti-configuration Project](/doc/images/multi-configuration-project.png)

## Extensions
Please refer to the [list of extensions points](https://jenkins.io/doc/developer/extensions/matrix-project/) of this plugin.


## Matrix Axis Extensions

[Dynamic Axis Plugin](https://plugins.jenkins.io/dynamic-axis)
This plugin allows you to define a matrix build axis that is dynamically populated from an environment variable:

[Selenium Axis Plugin](https://plugins.jenkins.io/selenium-axis)
Creates an axis based on a local Selenium grid and also build against the SauceLabs Selenium capability at the same time.
Both components rebuild before each build to take advantage of any new capabilities.

The Selenium grid uses all capabilities available and the SauceLab one a random subset, which can be configured or disabled.

[Yaml Axis Plugin](https://plugins.jenkins.io/yaml-axis)
Matrix project axis creation and exclusion plugin using yaml file (It's similar to .travis.yml)

[Sauce OnDemand Plugin](https://plugins.jenkins.io/sauce-ondemand)]
This plugin allows you to integrate Sauce Selenium Testing with Jenkins.

[Matrix Groovy Execution Strategy Plugin]
A plugin to decide the execution order and valid combinations of matrix projects.

[Elastic Axis]
This plugin is a power up for the multi configuration jobs allowing you to configure jobs to run on all slaves under a single label.Page:

[NodeLabel Parameter Plugin]
This plugin adds two new parameter types to job configuration - node and label, this allows to dynamically select the node where a job/project should be executed.

If your plug-in is not listed here, please file a PR for the README file.

## Version history
Please refer to the [changelog](/CHANGELOG.md)

# Matrix Project Plugin
In the New Item user interface, this plugin contributes the following one:
![Muti-configuration Project](/doc/images/multi-configuration-project.png)

## Multi-Configuration Projects
A multi-configuration project is useful for instances where your builds will make
 many similar build steps, and you would otherwise be duplicating steps.


## Configuration matrix
The Configuration Matrix allows you to specify what steps to duplicate,
 and create a multiple-axis graph of the type of builds to create.

For example, let us say that we have a build that we want to create 
for several different targets alpha, beta, and we want to produce both debug
and release outputs. With a freestyle job, we would have the following ant commands:

```
Ant: set-target-alpha debug compile
Ant: set-target-beta debug compile
Ant: set-target-gamma debug compile
Ant: set-target-alpha release compile
Ant: set-target-beta release compile
Ant: set-target-gamma release compile
```

We can reduce this to one ant target using variables:

```
Ant: $target $releasetype compile
```
And by adding two User Defined Axis in the Configuration Matrix:

Name: `target`
Value: `set-target-alpha set-target-beta set-target-gamma`

Name: `releasetype`
Value: `debug release`

The Names target and releasetype are exposed to the ant scripts as environment variables. The Value is 
a space delimited list of points to iterate through for each axis.

The immediate upshot is that if we have another release type to add (for example, debug-optimized), 
we can simply add the value to the releasetype user defined axis. The single change will add 
configuration to build the debug-optimized release type against all three values of target.

## Notes
* You have to choose "Build multi-configuration project" when creating a project, it can not
be changed later. If you skip this step, you will be very confused and not get very far.
* Each configuration is akin to an individual job.  It has its own build history, logs,
 environment, etc. The history of your multi-config job only shows you a list of configurations executed.
 You have to drill into each configuration to see the history and console logs.

## Executors used by a multi-configuration project
Reference: [Re: Will a multi-configuration / matrix job always use up one executor on the built-in node?](http://groups.google.com/group/jenkinsci-users/msg/eb809fb06759d861)

A matrix build project (that uses the Agent axis) will use one additional executor
on a random node to coordinate the executions on the nodes defined by the Agent axis.
This executor is added as a temporary executor (also called as "flyweight task") to the node and does not
use up a configured executor slot on this node (this behavior is controlled by hudson.model.Hudson.flyweightSupport).
Only this "flyweight task" will be affected by the "Restrict where this project can be run"
option, under Advanced Project Options. "Actual" or non-flyweight build execution can be controlled with 
the "Agents" axis that can be added under the project's Configuration Matrix: individual nodes and/or labels 
containing multiple nodes can be selected, as well as filtering axis combinations.

You can also control which nodes can run flyweight tasks,  using the [Exclude flyweight tasks plugin](https://plugins.jenkins.io/excludeMatrixParent), or you can pin the flyweight executor to a specific node (Please look under the "advanced" option of the matrix project configuration to tie the matrix parent to a label/agent). 

## Extensions
Please refer to the [list of extensions points](https://jenkins.io/doc/developer/extensions/matrix-project/) of this plugin.


## Matrix Axis Extensions

### [Dynamic Axis Plugin](https://plugins.jenkins.io/dynamic-axis)
This plugin allows you to define a matrix build axis that is dynamically populated from an environment variable:

### [Selenium Axis Plugin](https://plugins.jenkins.io/selenium-axis)
Creates an axis based on a local Selenium grid and also build against the SauceLabs Selenium capability at the same time.
Both components rebuild before each build to take advantage of any new capabilities.

The Selenium grid uses all capabilities available and the SauceLab one a random subset, which can be configured or disabled.

### [Yaml Axis Plugin](https://plugins.jenkins.io/yaml-axis)
Matrix project axis creation and exclusion plugin using yaml file (It's similar to .travis.yml)

### [Sauce OnDemand Plugin](https://plugins.jenkins.io/sauce-ondemand)
This plugin allows you to integrate Sauce Selenium Testing with Jenkins.

### [Matrix Groovy Execution Strategy Plugin](https://plugins.jenkins.io/matrix-groovy-execution-strategy)
A plugin to decide the execution order and valid combinations of matrix projects.

### [Elastic Axis](https://plugins.jenkins.io/elastic-axis)
This plugin is a power up for the multi configuration jobs allowing you to configure jobs to run on all agents under a single label.

### [NodeLabel Parameter Plugin](https://plugins.jenkins.io/nodelabelparameter)
This plugin adds two new parameter types to job configuration - node and label, this allows to dynamically select the node where a job/project should be executed.

If your plug-in is not listed here, please file a PR for the README file.

## External links

* [Experience with Hudson - Building matrix project](http://stackoverflow.com/questions/424295/experience-with-hudson-building-matrix-project)
* [Automated Builds using Windows](https://web.archive.org/web/20120626011127/http://blog.smartbear.com/software-quality/bid/169935/post/11-06-30/running-testcomplete-tests-with-multi-configuration-jenkins-projects) 



## Version history
Please refer to the [changelog](/CHANGELOG.md)

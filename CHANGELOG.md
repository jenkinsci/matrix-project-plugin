## Changelog

## Version 1.14 (Mar 6, 2019)

*   [Fix security issue](https://jenkins.io/security/advisory/2019-03-06/#SECURITY-1339)

## Version 1.12 (Oct 3, 2017)

*   [Align test result table headers where fields are](https://github.com/jenkinsci/matrix-project-plugin/commit/1d6412a216c85cee69c65e8fc0a213bba0026d90 "Align test result table headers where fields are")
*   [Allow MatrixAggregatable to be registered as an (optional) extension.](https://github.com/jenkinsci/matrix-project-plugin/commit/bdc61b83c4361711f3a909d156c75fcb22f0deb2 "Allow MatrixAggregatable to be registered as an (optional) extension.")
*   [Fix potential locking problem in MatrixProject](https://github.com/jenkinsci/matrix-project-plugin/commit/3966482d0272f2226b76767a62ede3266c2cf655 "Do not unlock what might not be locked")

## Version 1.11 (May 12, 2017)

*   [JENKINS-43990](https://issues.jenkins-ci.org/browse/JENKINS-43990) Upgrade to new parent pom and other related fixes

## Version 1.10 (Apr 19, 2017)

*   [JENKINS-39739](https://issues.jenkins-ci.org/browse/JENKINS-39739) Argument passed to createVariableResolver() must never be null
*   [JENKINS-43390](https://issues.jenkins-ci.org/browse/JENKINS-43390) Loading parameters from xml file causes that the same parameters are different objects for matrix configuration builds
*   [JENKINS-34389](https://issues.jenkins-ci.org/browse/JENKINS-34389) Fixed handling of dynamic axis

## Version 1.9 (Mar 24, 2017)

*   Optimized matrix configuration label computation
*   [JENKINS-34389](https://issues.jenkins-ci.org/browse/JENKINS-34389) Improved handling of axis rebuild

*   Fixed StringIndexOutOfBoundsException if label have no values
*   Make matrix build able to survive restarts

## Version 1.8 (Jan 12, 2017)

*   Fixed handling of dynamic axis
*   Fix race condition where A NPE is thrown when an item is being processed
*   Improved German Translation

## Version 1.7.1 (Jun 24, 2016)

*   [JENKINS-32230](https://issues.jenkins-ci.org/browse/JENKINS-32230) Disable WARNING log when folder is already existed

## Version 1.7 (May 24, 2016)

*   [JENKINS-34758](https://issues.jenkins-ci.org/browse/JENKINS-34758) Parameters visibility in child builds (related to SECURITY-170)

## Version 1.6 (Jun 18, 2015)

*   [JENKINS-JENKINS-28909](https://issues.jenkins-ci.org/browse/JENKINS-JENKINS-28909) Do not enforce safe characters for axis values (regression from 1.5).

## Version 1.5 (Jun 08, 2015)

*   Now requires 1.609+.
*   [JENKINS-25221](https://issues.jenkins-ci.org/browse/JENKINS-25221) Use new internal control.
*   Fix NullPointerException if swapping between MatrixExecutionStrategies
*   [JENKINS-25449](https://issues.jenkins-ci.org/browse/JENKINS-25449) Better form validation.
*   [JENKINS-13554](https://issues.jenkins-ci.org/browse/JENKINS-13554) Cleaner handling of build deletion.
*   [JENKINS-23614](https://issues.jenkins-ci.org/browse/JENKINS-23614) URL escaping fix.
*   [JENKINS-9277](https://issues.jenkins-ci.org/browse/JENKINS-9277) [JENKINS-23614](https://issues.jenkins-ci.org/browse/JENKINS-23614) [JENKINS-25448](https://issues.jenkins-ci.org/browse/JENKINS-25448) Reject invalid axis name/value.
*   Display axis name in tooltip.
*   [JENKINS-27162](https://issues.jenkins-ci.org/browse/JENKINS-27162) Log touchstone build results.
*   [JENKINS-26582](https://issues.jenkins-ci.org/browse/JENKINS-26582) Errors when starting builds from Git commit notifications.

## Version 1.4.1 (Feb 27, 2015)

Bundled in 1.596.1 and 1.600. **Do not update to this release if using an older Jenkins version**. If you have already updated, 
use _Plugin Manager » Installed_ to revert to your previous version.


*   [Fixed a security issue related to the combinations filter script](https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2015-02-27). You need to update Jenkins to 1.596.1 or 1.600 to get this fix.

## Version 1.4 (Oct 14, 2014)

*   Automatic deletion of inactive configurations from the disk
*   [JENKINS-24282](https://issues.jenkins-ci.org/browse/JENKINS-24282) Use noun phrase in the New Item dialog
*   [JENKINS-19179](https://issues.jenkins-ci.org/browse/JENKINS-19179) Prevent the disabling of matrix configurations (e.g. by checkout failures in Subversion plugin)

## Version 1.3 (Jul 22, 2014)

*   Moved in a file which was accidentally left behind in core.
*   Preparing for possible split of test reporting functionality into a plugin.
*   Portuguese translation fix.

## Version 1.2 (May 07, 2014)

*   [JENKINS-22798](https://issues.jenkins-ci.org/browse/JENKINS-22798) Fixed another class loading problem related to split.

## Version 1.1 (May 06, 2014)

*   [JENKINS-22863](https://issues.jenkins-ci.org/browse/JENKINS-22863) Fixed class loading problem affecting combination filters after split.

## Version 1.0 (Apr 28, 2014)

*   Depending on 1.561 final.

## Version 1.0 beta 1 (Apr 14, 2014)

*   Split off from core as of 1.561.

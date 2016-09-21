/* **DISCLAIMER**
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
* HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
* Without limitation of the foregoing, Contributors/Regents expressly does not warrant that:
* 1. the software will meet your requirements or expectations;
* 2. the software or the software content will be free of bugs, errors, viruses or other defects;
* 3. any results, output, or data provided through or generated by the software will be accurate, up-to-date, complete or reliable;
* 4. the software will be compatible with third party software;
* 5. any errors in the software will be corrected.
* The user assumes all responsibility for selecting the software and for the results obtained from the use of the software. The user shall bear the entire risk as to the quality and the performance of the software.
*/ 

def clientVersion() {
    return "2.6.4"
}

/**
* Foscam Motion Alarm Monitor
*
* Author: RBoy
* Copyright RBoy, redistribution of any changes or modified code is not allowed without permission
* 2016-6-27 - Improve SHM integration
* 2016-6-23 - Allow you to change the name of the app
* 2016-6-7 - Correct mode name of SHM to Disarm in options
* 2016-3-18 - Debug message fix
* 2016-3-8 - Added integration with SHM for arming/disarming camera
* 2016-3-7 - Updated the push callback URL for SD cameras as per the new ST format
* 2016-3-7 - Only kick start monitor task from heartbeat if the monitor task is active
* 2016-3-6 - Improve heartbeat reliability
* 2016-3-5 - Brand new scheduling system to make motion detection more reliable and resistant to the broken ST platform timer issues
* 2016-1-17 - Can send SMS's to multiple people, separate with + e.g. 5551234567+4447654321
* 2016-1-10 - Included hideable sections in the setup options for better layout
* 2016-1-9 - Added support for changing camera presets based on people (presence sensors) arrival notifications
* 2015-11-2 - Added notification for alarm
* 2015-10-25 - Added support for assigning presets for each mode
* 2015-9-26 - Updated layout and added explanations
* 2015-9-26 - Added a 2 second delay before starting cruise when motion alarm is detection to avoid picture tearing
* 2015-9-10 - Updated to comlpy with new platform changes for API Server URL
* 2015-9-1 - Added option to turn off Light after some time (configurable), improved SD camera push motion callback status update
* 2015-8-25 - Fixed support for push notifications with SD Foscam (finally)
* 2015-8-24 - Pull is the default mode for checking for Alarms, user can force Push mode if camera supports it (but may not work currently due to URL shortening limitations of Foscam)
* 2015-8-24 - Ignore motion events from camera's not being actively monitored (phantom events)
* 2015-8-23 - Fixed issue with Push notifications not updating status
* 2015-8-23 - Added option to Start/Stop camera cruise on external alarms
* 2015-8-23 - Added support for Modes to disable alarms when not in selected modes
* 2015-8-23 - Added support for Shortened URL's for push notifications to workaround the 128 character limit for SD cameras and option to disable Push notifications in the settings
* 2015-8-23 - Added support for Camera pull/push notifications
* 2015-8-14 - Added support for SD Camera's Motion Detection support
* 2015-8-13 - Fix for Android ST app not able to install due to broken platform changes
* 2015-8-12 - Added support to reset to the specified present position when the motion detection is disabled
* 2015-7-26 - Added support to start cruise when motion alarm is detected
* 2015-6-19 - Added support for taking pictures
* 2015-6-18 - Initial version
*/

definition(
    name: "Foscam Motion Alarm Monitor",
    namespace: "RBoy",
    author: "RBoy",
    description: "Forces the foscam device to check if the motion sensor alarm has been set off",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png"
)

preferences {
    page(name: "mainPage")
    page(name: "modePresetsPage")
    page(name: "presencePresetsPage")
}

import groovy.json.JsonSlurper
import groovy.transform.Synchronized

def mainPage() {
    dynamicPage(name: "mainPage", title: "Foscam Motion Alarm Monitor v${clientVersion()}", install: true, uninstall: true) {    
        section("About") {
            paragraph "This smart app is used in conjunction with a Foscam device type to force the device type to check if the Foscam camera(s) (HD and non-HD) has a motion alarm that has been set off."
            paragraph "The motion alerts only works when the MOTION DETECTION feature on the Foscam Camera Device is active. When the Motion Detection feature on the device is disabled, this app is dormant and will not check for any motion alerts."
        }

        section("Foscam Alarm Monitor Settings") {
            input "cameras", "capability.imageCapture", title:"Select Foscam Camera to Monitor", multiple:true, required:true, submitOnChange: true
            input "interval", "number", title:"Motion alarm check interval (in seconds)", defaultValue:5
        }

        def cruiseNames = getCruiseNames()
        def presetNames = getPresetNames()
        section("Motion Detected Action Settings (optional)", hidden: ((sms || push || alarms || alarmSilent || lights || picture || cruisemap || resetPreset) ? false : true), hideable: true) {
            paragraph "You can enter multiple phone numbers to send an SMS to by separating them with a '+'. E.g. 5551234567+4447654321"
            input "sms", "phone", title: "Send SMS Notification to", required: false
            input "push", "bool", title: "Send Push Notification", required: false
            paragraph "Select the external Alarm device to activate (sound and strobe) when motion is detected on the camera. When Silent Alarm is enabled, only the strobe light is activated."
            input "alarms", "capability.alarm", title: "Alarm to turn on if motion is detected", multiple: true, required: false
            input "alarmSilent", "bool", title: "Silent Alarm", required: false
            paragraph "Select the lights or switches to activate when motion is detected on the camera"
            input "lights", "capability.switch", title: "Select lights/switches to turn on if motion is detected", multiple: true, required: false, submitOnChange:true
            if (lights) {
                input "lightTimer", "number", title: "Turn off lights/switches after (minutes)", description: "Leave empty to not turn off lights/switches", required: false
            }
            paragraph "Enable this option to have SmartThings take a picture when motion is detected on the camera"
            input "picture", "bool", title: "Take picture if motion is detected", required: false
            paragraph "This option allows you to start a the camera cruise action (if configured on the camera) when motion is detected."
            input "cruisemap", "enum", title: "Cruise action when motion is detected", options: cruiseNames, description: "Start this cruise action when motion alarm is set off", required: false
            paragraph "Use this option to reset the camera to the selected position (if configured on the camera) the Motion Detection feature has been disabled on the camera"
            input "resetPreset", "enum", title: "Reset position when motion detection is disabled", options: presetNames, description: "When motion detection is stopped, set the camera to this preset position", required: false
        }
        section("Start/Stop Camera Cruise on External Alarm (optional)", hidden: (extAlarms ? false : true), hideable: true) {
            paragraph "Use this to start/stop the camera cruise actions and reset the camera position when an external alarm device is turned on/off"
            input "extAlarms", "capability.alarm", title: "When this external Alarm(s) turns on/off...", multiple: true, required: false, submitOnChange:true
            if (extAlarms) {
                input "extCruisemap", "enum", title: "Start/stop this cruise...", options: cruiseNames, description: "Start this cruise action when external alarm(s) is set off", required: false
                input "extResetPreset", "enum", title: "Reset the camera to this position when Alarm(s) is turned off", options: presetNames, description: "When Alarm(s) is turned off, set the camera to this preset position", required: false
            }
        }
        section ("Mode based presets (optional)", hidden: (modePresetsDescription() ? false : true), hideable: true){
            paragraph "Use this page to set the camera to specific presets for each mode"
            href(name: "toModePresets", title: "Assign camera preset for modes", page: "modePresetsPage", description: modePresetsDescription(), required: false)
        }
        section ("Presence based presets (optional)", hidden: (presencePresetsDescription() ? false : true), hideable: true){
            paragraph "Use this page to set the camera to specific presets when people arrive"
            href(name: "toPresencePresets", title: "Assign camera preset for people", page: "presencePresetsPage", description: presencePresetsDescription(), required: false)
        }
        section("Smart Home Monitor (SHM) Integration") {
            paragraph "Use these options to Enable/Disable Motion Detection when SHM is Armed/Disarmed"
            input "armAway", "bool", title: "Enable Motion Detection on Away", required: false
            input "armStay", "bool", title: "Enable Motion Detection on Stay", required: false
            input "disarmOff", "bool", title: "Disable Motion Detection on Disarm", required: false
        }
        section("Alarm Detection Method (optional)", hidden: (enablePush ? false : true), hideable: true) {
            paragraph "Some Foscam camera's (like MJPEG SD cameras) support Push notifications. Enable this option to use push alarm notifications which are more reliable. If push alarm notifications are not working, try turning it off. Make sure you have configured a valid DNS server on camera to use this."
            input "enablePush", "bool", title: "Use Push Alarm Detection Mechanism", description: "Enable this option if your camera is able to send push notifications (check DNS settings on camera)", required: false, defaultValue: true
        }
        section("Motion Detection Modes (optional)", hidden: (modeMonitor ? false : true), hideable: true) {
            paragraph "Enable Motion Detection and Alarm notifications/actions only when operating in the following modes."
            input name: "modeMonitor", title: "Monitor only when in this mode(s)", type: "mode", required: false, multiple: true
        }
        section("Change Name of App (optional)") {
            label title: "Assign a name", required: false
        }
    }
}

def modePresetsPage() {
    dynamicPage(name: "modePresetsPage", title: "", install: false, uninstall: false) {    
        section("About") {
            paragraph "Use this page to reset the camera to specific presets for each specified mode. Leave it the preset blank for a mode if you don't want a specific preset assigned to a specific mode."
        }

        section ("Assign camera preset for each mode"){
            def presetNames = getPresetNames()
            for (mode in location.modes) {
                def lastPreset = settings."preset${mode}"
                log.trace "Last preset for $mode is $lastPreset"
                if (lastPreset) {
                    input name: "preset${mode}", type: "enum", title: "Mode ${mode}", options: presetNames, defaultValue: lastPreset, description: "Select preset", required: false
                } else {
                    input name: "preset${mode}", type: "enum", title: "Mode ${mode}", options: presetNames, description: "Select preset", required: false
                }
            }
        }
    }
}

def presencePresetsPage() {
    dynamicPage(name: "presencePresetsPage", title: "", install: false, uninstall: false) {    
        section("About") {
            paragraph "Use this page to reset the camera to specific presets based on arrival notifications."
        }

        section ("Assign camera preset for each person"){
            def presetNames = getPresetNames()
            input name: "peoplePresets", type: "capability.presenceSensor", title: "Select people to assign presets", description: "Select preset", multiple: true, required: false, submitOnChange:true
            for (person in settings."peoplePresets") {
                def lastPreset = settings."preset${person}"
                log.trace "Last preset for $person is $lastPreset"
                if (lastPreset) {
                    input name: "preset${person}", type: "enum", title: "${person} arrives", options: presetNames, defaultValue: lastPreset, description: "Select preset", required: false
                } else {
                    input name: "preset${person}", type: "enum", title: "${person} arrives", options: presetNames, description: "Select preset", required: false
                }
            }
        }
    }
}

private int getActiveAlarmPollPeriod() {
    return 15 // poll 15 seconds
}

private String modePresetsDescription() {
    def pieces = ""
    for (mode in location.modes) {
        def preset = settings."preset${mode}"
        if (preset) {
            if (pieces.length() > 0) {
                pieces += "\n"
            }
            pieces += "$mode - $preset"
        }/* else {
if (pieces.length() > 0) {
pieces += "\n"
}
pieces += "$mode - <Not set>"
}*/
    }

    return pieces
}

private String presencePresetsDescription() {
    def pieces = ""
    for (person in settings."peoplePresets") {
        def preset = settings."preset${person}"
        if (preset) {
            if (pieces.length() > 0) {
                pieces += "\n"
            }
            pieces += "$person - $preset"
        }
    }

    return pieces
}

private getCruiseNames()
{
    log.trace "Getting cruise names from camera $cameras"
    def ret = []
    if (cameras == null) {
        ret.add("Invalid! Select/Refresh Camera")
    } else {
        ret.add(cameras[0].currentCruise1)
        ret.add(cameras[0].currentCruise2)
    }
    log.trace "Cruise names -> ${ret}"
    return ret
}

private getPresetNames()
{
    log.trace "Getting present names from camera $cameras"
    def ret = []
    if (cameras == null) {
        ret.add("Invalid! Select/Refresh Camera")
    } else {
        ret.add(cameras[0].currentPresetA)
        ret.add(cameras[0].currentPresetB)
        ret.add(cameras[0].currentPresetC)
    }
    log.trace "Preset names -> ${ret}"
    return ret
}

private setCameraToPreset(camera, resetPreset)
{
    if (camera.currentPresetA == resetPreset) {
        log.debug "Setting camera $camera to preset 1 -> $resetPreset"
        camera.preset1()
    } else if (camera.currentPresetB == resetPreset) {
        log.debug "Setting camera $camera to preset 2 -> $resetPreset"
        camera.preset2()
    } else if (camera.currentPresetC == resetPreset) {
        log.debug "Setting camera $camera to preset 3 -> $resetPreset"
        camera.preset3()
    }
}

def installed() {
    log.debug "installed called"
    initialize()
}

def updated() {
    log.debug "updated called"
    unsubscribe()
    unschedule()
    initialize()
}

private def initialize() {
    log.debug "Initialize with settings: ${settings}"
    log.debug "Selected Modes: $modeMonitor"

    setupCameraAccessToken()

    state.cameraList = [] // Reset the list

	subscribe(location, "sunrise", monitorTask)
	subscribe(location, "sunset", monitorTask)
    subscribe(location, "mode", modeChangeHandler)
    subscribe(cameras, "alarmStatus.on", startMonitor)
    subscribe(cameras, "alarmStatus.off", stopMonitor)
    subscribe(cameras, "motion.active", motionDetected)
    subscribe(extAlarms, "alarm", extAlarmNotify)
    subscribe(peoplePresets, "presence.present", arrivalNotify) // People arrival notification
    subscribe(location, "alarmSystemStatus", shmHandler) // SHM Integration

    state.lastMotionMonitorCheck = 0
    state.lastHeartBeat = 0
    
    // Check if any of the camera's have their alarmStatus as not off (on or alarm), if so then add them to the list and start the monitoring
    for (camera in cameras) {
        log.debug "Camera $camera type is ${camera.currentAlarmNotifyType} and current motion monitoring status is ${camera.currentValue("alarmStatus")}"
        if (camera.currentValue("alarmStatus") != "off") {
            log.debug "Adding camera $camera.id to the monitor camera list ${state.cameraList}"
            state.cameraList.add(camera.id) // add camera to be monitored
        }
    }

    if (state.cameraList.size() > 0) { // If we have any camera's in the list start the monitorTask
        log.debug "Found active cameras in the monitor list, starting monitor task"
        runIn(interval, monitorTask)
    }
}

// Integration with SHM
def shmHandler(evt) {
    log.trace "SHM Handler called with state $evt.value"
    
    switch (evt.value) {
        case "stay":
        	if (armStay) {
                log.trace "Enabling All Cameras Motion Detection on Stay"
                cameras?.on()
            } else {
                log.trace "No action configured for Stay"
            }
            break

        case "away":
        	if (armAway) {
                log.trace "Enabling All Cameras Motion Detection on Away"
                cameras?.on()
            } else {
                log.trace "No action configured for Away"
            }
            break

        case "off":
        	if (disarmOff) {
                log.trace "Disabling All Cameras Motion Detection on Away"
                cameras?.off()
            } else {
                log.trace "No action configured for Away"
            }
            break
            
        default:
            log.error "Invalid SHM state: $evt.value"
            break
    }
}

def startMonitor(event) {
    log.trace "startMonitor called, starting status updated for camera ${event.displayName}"

    def camera = cameras.find { event.deviceId == it.id }
    if (!camera) {
        log.error "Motion even is from Camera ${event.displayName} whic is not in the list of actively monitored camera's $cameras, this should not happen"
        return
    }

    if (!state.cameraList.contains(camera.id)) {
        log.trace "Adding camera ${camera.id} to monitor camera list ${state.cameraList}"
        state.cameraList.add(event.device.id) // add camera to be monitored
    }
    else
        log.warn "Camera ${camera} is already in the monitored list"

    log.trace "Calling monitorTask"
    monitorTask()
}

def stopMonitor(event) {
    // Start with this to avoid a race condition with events flowing in
    log.trace "StopMonitor called for camera ${event.displayName}, removing camera ${event.device.id} from monitor camera list ${state.cameraList}"
    def devMonitored

    devMonitored = state.cameraList.remove(event.device.id) // remove camera to stop monitoring
    log.trace "Updated monitor camera list ${state.cameraList}"

    def camera = cameras.find { event.deviceId == it.id }
    if (!camera) {
        log.error "Motion even is from Camera ${event.displayName} whic is not in the list of actively monitored camera's $cameras, this should not happen"
        return
    }

    // Check if the camera is in the list of actively monitored camera (phantom event check)
    if (!devMonitored) {
        log.warn "Received motion event from a camera $camera that no longer being actively monitored"
        return
    }

    //log.trace "Reconfiguring camera $camera"

    // First stop more notifications, push is not reliable to we stick to Poll as default for now
    if (enablePush && (camera.currentAlarmNotifyType == "Push")) {
        log.debug "Deregistering motion event callback"
        camera.deRegisterMotionCallback()
    }

    // Stop the cruise action if enabled
    if (cruisemap) {
        log.debug "Stopping cruise on $camera"
        camera.stopCruise()
    }

    if (resetPreset) {
        log.trace "Checking Reset position $resetPreset on camera ${event.device.id} from monitor camera ${camera}"
        setCameraToPreset(camera, resetPreset)
    }

    if (state.cameraList.size() == 0) {
        log.trace "Stopping monitorTask since there are no cameras to be monitored"
        unschedule()
    }
}

def monitorTask() {
    //log.trace "Monitor task called"

    try { // This can take a long time sometimes, don't let it kill if it times out
        // Hack for broken ST timers - Schedule the Heartbeat
        if (((state.lastHeartBeat ?: 0) + ((1+1)*60*1000) < now()) && canSchedule()) { // Since we are scheduling the heartbeat every 1 minutes, give it a 1 minute grace
            log.warn "Heartbeat not called in last 2 minutes, rescheduling heartbeat"
            schedule("* */1 * * * ?", heartBeatMonitor) // run the heartbeat every 1 minutes
            state.lastHeartBeat = now() // give it 1 minutes before you schedule it again
        }

        if (modeMonitor && !modeMonitor.contains(location.mode)) { // Empty means all modes
            log.warn "Current mode ${location.mode} is not in the list of active monitoring modes $modeMonitor, skipping checking for Monitor Alarms"
            return
        }

        cameras.each { camera ->
            if (state.cameraList.contains(camera.id)) {
                if (!enablePush || (camera.currentAlarmNotifyType == "Pull")) {
                    log.debug "Checking Camera $camera for Active Motion Alarms"
                    camera.checkMotionStatus()
                } else {
                    // Camera will call this URL
                    setupCameraAccessToken() // We need a new URL each time otherwise it caches it and won't work
                    //def callbackURL = "http://graph.api.smartthings.com/api/token/${state.accessToken}/smartapps/installations/${app.id}/Motion/${camera.id}"
                    //def callbackURL = apiServerUrl("/api/token/${state.accessToken}/smartapps/installations/${app.id}/Motion/${camera.id}")
                    def callbackURL = apiServerUrl("/api/smartapps/installations/${app.id}/Motion/${camera.id}?access_token=${state.accessToken}") // As per the new format
                    def shortURL = shortenURL(callbackURL)
                    def visitURL = LongURLService(shortURL)
                    log.debug "Registering Camera Motion Callback URL -> ${URLEncoder.encode(visitURL)}"
                    camera.registerMotionCallback(URLEncoder.encode(visitURL))
                }
            }
        }

        state.lastMotionMonitorCheck = now() // Update the last time we successfully checked for motion events

        if (state.cameraList.size() > 0) {
            //log.trace "Monitor camera list ${state.cameraList}"
            cameras.each { camera ->
                if (state.cameraList.contains(camera.id)) {
                    if (!enablePush || (camera.currentAlarmNotifyType == "Pull")) {
                        //log.trace "ReScheduling monitorTask to run in ${interval} seconds"
                        runIn(interval, monitorTask) // runIn has better resolution than runOnce but less reliable given current ST platform status
                        return // done here
                    }
                }
            }
        }
    } catch (e) {
        log.error "Monitor task was unsuccessful, will try again shortly. Error:$e"
        runIn(interval, monitorTask) // try this again and hope it doesn't timeout
    }
}

// Heartbeat system to ensure that the MonitorTask doesn't die when it's supposed to be running
def heartBeatMonitor() {
    log.trace "Heartbeat monitor called"
    
    state.lastHeartBeat = now() // Save the last time we were executed

    // Kick start the motion detection monitor if didn't update for more than 30 seconds beyond the polling period
    if (state.cameraList.size() > 0) { // If we are supposed to be in monitoring mode
        log.trace "Last motion detection check was done " + ((now() - (state.lastMotionMonitorCheck ?: 0))/1000) + " seconds ago"
        if ((((state.lastMotionMonitorCheck ?: 0) + (interval * 1000) + (30*1000)) < now()) && canSchedule()) {
            log.trace "Scheduling a backup motion detection scheduler in 30 seconds" 
            runIn(3*60, backupMotionDetectionScheduler) // Schedule a backup in 3 minutes (just in case the heartbeat dies this should help revive it

            log.warn "Motion detection hasn't been run a long time, calling it to kick start it"
            monitorTask()
        }
    }
}

// Backup scheduler (incase the original is overloaded)
def backupMotionDetectionScheduler() { 
	log.trace "Backup Motion Detection Scheduler"
	monitorTask()
}

def pollActiveCameras() {
    log.trace "Poll active camera's manually"

    cameras.each { camera ->
        if (state.cameraList.contains(camera.id)) {
            log.debug "Manual checking camera $camera for Active Motion Alarms"
            camera.checkMotionStatus()
            if (camera.currentMotion == "active") { // As long as are active keep polling (this function is called from Push mode so we need to track until it's inactive)
                log.trace "Push mode manual poll alarm still active, checking for an status update in ${getActiveAlarmPollPeriod()} seconds"
                runIn(getActiveAlarmPollPeriod(), pollActiveCameras)
            }
        }
    }
}

def motionDetected(event) {
    log.info "Motion detected in camera ${event.displayName}"

    def camera = cameras.find { event.deviceId == it.id }
    if (!camera) {
        log.error "Motion event is from Camera ${event.displayName} whic is not in the list of actively monitored camera's $cameras, this should not happen"
        return
    }

    // Synchronize these lists otherwise we have a race condition
    synchronized(state) {
        // Check if the camera is in the list of actively monitored camera (phantom event check)
        if (state.cameraList.size() > 0) {
            if (!state.cameraList.contains(camera.id)) {
                log.warn "Received motion event from a camera $camera that no longer being actively monitored"
                return
            }
        } else {
            log.warn "Received motion event from a camera $camera, no cameras are being actively monitored"
            return
        }
    }

    log.trace "Camera id ${camera.id}, active camera list ${state.cameraList}"

    // turn on the alarms
    log.debug "Turning on alarms $alarms, silent: $alarmSilent"
    alarmSilent ? alarms?.strobe() : alarms?.both()

    // turn on lights
    log.debug "Turning on lights $lights"
    lights?.on()
    if (lightTimer) {
        log.trace "Scheduling lights turn off after $lightTimer minutes"
        runIn(lightTimer * 60, turnOffLights)
    }

    // take picture
    if (picture) {
        log.debug "Taking a picture with camera $event.displayName"
        camera.take()
    }

    // turn on cruise
    if (cruisemap) {
        // Delay for about a second before starting the cruise to avoid tearing of the picture
        smallDelay(2)

        if (getCruiseNames().findIndexOf { it == cruisemap } == 0) {
            camera.cruisemap1()
            log.debug "Enabling cruise $cruisemap from CruiseMap1"
        } else {
            camera.cruisemap2()
            log.debug "Enabling cruise $cruisemap from CruiseMap2"
        }
    }

    // Send notifications
    log.debug "SMS: $sms, Push: $push"
    def message = "${event.displayName} has detected motion"
    sms ? sendText(sms, message) : ""
    push ? sendPush(message) : sendNotificationEvent(message)

    // If we are in push mode then we need to manually check in X seconds if the motion alarm notification still exists otherwise nothing will update
    if (enablePush && (camera.currentAlarmNotifyType == "Push")) {
        monitorTask() // Setup the new token URL in the camera
        log.trace "Push mode, checking for an status update in ${getActiveAlarmPollPeriod()} seconds"
        runIn(getActiveAlarmPollPeriod(), pollActiveCameras)
    }
}

def turnOffLights() {
    log.debug "Schedule called, turning off lights $lights"
    lights?.off()
}

def extAlarmNotify(evt) {
    log.info "External alarm ${evt.displayName} set alarm to ${evt.value}"

    // Cruise maps start/stop
    if (extCruisemap) {
        if (evt.value == "off") {
            cameras.each { camera -> // turn off all cameras
                // Stop the cruise action if enabled
                log.debug "External Alarm turned off, stopping cruise on $camera"
                camera.stopCruise()
            }
        }
        else { // turn on cruise actions for all other strobe/on/both actions for all cameras
            cameras.each { camera -> // turn off all cameras
                // Start the cruise action if enabled
                if (getCruiseNames().findIndexOf { it == extCruisemap } == 0) {
                    camera.cruisemap1()
                    log.debug "External Alarm ${evt.value}, starting cruise $extCruisemap on $camera from CruiseMap1"
                } else {
                    camera.cruisemap2()
                    log.debug "External Alarm ${evt.value}, starting cruise $extCruisemap on $camera from CruiseMap2"
                }
            }
        }
    }

    // Reset to preset when the alarms are turned off
    if (extResetPreset) {
        if (evt.value == "off") {
            cameras.each { camera -> // reset all cameras
                log.trace "Checking reset position $extResetPreset on camera $camera because External Alarm turned off"
                setCameraToPreset(camera, extResetPreset)
            }
        }
    }
}

def modeChangeHandler(evt) {
    def mode = evt.value
    log.info "Mode changed to: ${mode}, reinitializing the Monitor Task and settings camera preset if configured"
    def presetMode = settings."preset${mode}"
    if (presetMode) {
        cameras.each { camera -> // reset all cameras
            log.info "Resetting camera $camera to preset $presetMode"
            setCameraToPreset(camera, presetMode)
        }
    }

    monitorTask() // check if we need to reinitialize the Monitoring
}

def arrivalNotify(evt) {
    log.info "$evt.displayName arrived, presence set to $evt.value"
    def person = evt.device
    def presetMode = settings."preset${person}"
    if (presetMode) {
        cameras.each { camera -> // reset all cameras
            log.info "Resetting camera $camera to preset $presetMode"
            setCameraToPreset(camera, presetMode)
        }
    }

    monitorTask() // check if we need to reinitialize the Monitoring  
}

// OAuth Configuration for Push Camera incoming Web notifications for motion alarms
mappings {
    path("/Motion/:cameraId") {
        action: [
            GET: "cameraMotionCallback"
        ]
    }
}

// OAuth call back from external REST webservice
def cameraMotionCallback() {
    log.info "Received motion detected callback for camera Id ${params.cameraId} with params -> $params"
    setupCameraAccessToken() // We need a new URL each time it is set off otherwise it caches it and won't work the next time
    cameras.each { camera ->
        if (camera.id == params.cameraId) {
            log.info "Motion Detected on Camera $camera"

            // Check if the camera is in the list of actively monitored camera (phantom event check)
            if (state.cameraList.size() > 0) {
                if (!state.cameraList.contains(camera.id)) {
                    log.warn "Received motion event from a camera $camera that no longer being actively monitored"
                    return
                }
            } else {
                log.warn "Received motion event from a camera $camera, no cameras are being actively monitored"
                return
            }

            camera.motionCallbackNotify() // Let the device know we got a callback notification
        }
    }
}

private setupCameraAccessToken() {
    log.trace "Creating Access Token for call back" // For security purposes each time we initialize we create a new token
    createAccessToken() 

    // Each Camera will call this URL with their Id's
    cameras.each { camera ->
        //def callbackURL = "http://graph.api.smartthings.com/api/token/${state.accessToken}/smartapps/installations/${app.id}/Motion/${camera.id}"
        //def callbackURL = apiServerUrl("/api/token/${state.accessToken}/smartapps/installations/${app.id}/Motion/${camera.id}")
        def callbackURL = apiServerUrl("/api/smartapps/installations/${app.id}/Motion/${camera.id}?access_token=${state.accessToken}") // New format per documentation
        
        log.trace "Camera $camera Motion Callback URL -> $callbackURL"
    }
}

// Shorten the URL since Foscam cameras cannot handle a URL greater than 128 characters
private String shortenURL(longURL) {
    def params = [
        uri: 'http://tiny-url.info/api/v1/create',
        contentType: 'application/json',
        query: [apikey:'D4AG7G09FA819E00F77C', provider: 'tinyurl_com', format: 'json', url: longURL]
    ]

    try {
        httpGet(params) { response ->
            //log.trace "Request was successful, data=$response.data, status=$response.status"
            if (response.data.state == "ok") {
                log.trace "Short URL: ${response.data.shorturl}"
                log.trace "Long URL: ${response.data.longurl}"
                return response.data.shorturl
            } else {
                log.error "Error in return short URL: ${response.data}"
            }
        }
    } catch (e) {
        log.error "Error getting shortened URL: $e"
    }
}

// This service when called visits the shortURL to lengthen it and in the process activates the link
private String LongURLService(shortURL) {
    def visitURL = "http://api.longurl.org/v2/expand?title=1&url=" + shortURL
    log.trace "Visit URL: $visitURL"
    return visitURL
}

// Small local delay upto 10 seconds by looping into an external website (only minimum/atleast is guaranteed, it could exceed this amount)
private void smallDelay(int delayInSeconds) {
    def params = [
        uri: (delayInSeconds > 2 ? "http://www.egifts2u.com/" : "http://www.bluehost.com/")
    ]

    def retVal = null

    if (delayInSeconds > 10) {
        log.warn "The maximum delay is 10 seconds, setting to 10 seconds"
        delayInSeconds = 10
    }

    if (delayInSeconds < 1) {
        log.warn "The minimum delay is 1 second, setting to 1 second"
        delayInSeconds = 1
    }

    def start = now() // ms
    //log.trace "Starting count at $start"

    while (true) {
        try { // thanks @CosmicPuppy
            retVal = httpGet(params) { response ->
                //log.trace "Request was successful, data=$response.data, status=$response.status"
            }
        }
        catch (Exception e) {
            //log.trace("Unable to contact website, Error: $e")
        }

        def end = now() // ms
        //log.trace "Ended count at $end"

        if ((end - start)/1000 > delayInSeconds) {
            log.debug "Successfully delayed by ${(end - start)/1000} seconds"
            return
        }
        else {
            log.trace "Delayed by ${(end-start)/1000} seconds, looping another delay"
        }
    }
}

private sendText(number, message) {
    if (sms) {
        def phones = sms.split("\\+")
        for (phone in phones) {
            sendSms(phone, message)
        }
    }
}
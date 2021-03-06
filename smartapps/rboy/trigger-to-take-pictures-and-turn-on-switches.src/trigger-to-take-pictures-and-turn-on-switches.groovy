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
 
 /**
 *  Use a trigger to activate camera to take pictures, activate motion monitoring, turn on switches and send a notification
 *
 *  Copyright 2015 RBoy
 *  Change log: Version 1.1.0
 *  2015-12-30 - Added support for taking multiple pictures (burst) 0.5 seconds apart
 *  2015-12-30 - Added support for taking a picture on acceleration
 *  2015-12-23 - Added support to take a picture on the push of a momentary switch (e.g. door bell)
 *  2015-7-22 - Added support to turn on switches and camera monitoring
 *  2015-1-30 - Initial code
 */
definition(
    name: "Trigger to take Pictures and turn on Switches",
    namespace: "rboy",
    author: "RBoy",
    description: "Use a trigger (motion sensor, door sensor etc) to activate a camera to take pictures and turn on motion monitoring, switches and send a notification",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Categories/cameras.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Categories/cameras@2x.png"
)

preferences {
	section("Choose Trigger Events") {
    	paragraph "Choose motion sensors and contact sensors which will trigger the actions in the next section"
		input "motionSensors", "capability.motionSensor", title: "If motion is detected here...", multiple: true, required: false
		input "contactSensors", "capability.contactSensor", title: "If contact is opened/closed here...", multiple: true, required: false
		input "momentarySwitches", "capability.momentary", title: "If a button is pushed here...", multiple: true, required: false
        input "accelerationSensors", "capability.accelerationSensor", title: "If acceleration detected here...", multiple: true, required: false
	}

	section("Choose Trigger Actions") {
    	paragraph "Choose cameras and switches which will take pictures and turn on when events are triggered from the previous section"
		input "cameras", "capability.imageCapture", title: "...take pictures with these cameras", multiple: true, required: false
        input "burstCount", "number", title: "...how many pictures?", defaultValue:3
		input "cameraMonitoring", "bool", title: "...optionally turn on camera motion monitoring"
		input "switches", "capability.switch", title: "...turn on these switches", multiple: true, required: false
        input
	}

	section("Notification Options") {
    	input "notification", "bool", title: "Send notification"
    	input "phone", "phone", title: "Phone number to send SMS to (optional)", required: false
    }

}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    subscribe(motionSensors, "motion.active", motionHandler)
    subscribe(contactSensors, "contact.open", contactHandler)
    subscribe(momentarySwitches, "momentary.pushed", momentaryHandler)
    subscribe(accelerationSensors, "acceleration.active", accelerationHandler)
}

def accelerationHandler(evt) {
	log.debug "Acceleration detected, activating cameras"
    takeAction(evt)
}

def motionHandler(evt) {
	log.debug "Active motion detected, activating cameras"
    takeAction(evt)
}

def contactHandler(evt) {
	log.debug "Open door detected, activating cameras"
    takeAction(evt)
}

def momentaryHandler(evt) {
	log.debug "Button push detected, activating cameras"
    takeAction(evt)
}

private takeAction(evt) {
    for(camera in cameras) {
    	log.info "$evt.displayName detected $evt.value, activating camera $camera to take pictures"
		camera.take()
        try {
            if (cameraMonitoring) {
                log.info "Turning on camera monitoring for camera $camera"
                camera.on()
            }
        } catch (all) {
        	log.error "Camera $camera does not support motion monitoring"
        }
    }

    for(switchon in switches) {
    	log.info "$evt.displayName detected $evt.value, turning on switch $switchon"
		switchon.on()
    }
    
    (1..((burstCount ?: 3) - 1)).each {
        for(camera in cameras) { // Do this in the end because it causes a delay
            log.info "Taking burst picture $it with camera $camera"
            camera.take(delay: (500 * it))
        }
    }

    def distressMsg = "$evt.displayName detected $evt.value"
    if (phone) {
        sendSms(phone, distressMsg)
    }
    if (notification) {
        sendPush "$distressMsg"
    }
}
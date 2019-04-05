/**
 *
 *  Nano Dimmer (Aeotec Inc)
 *
 *	github: Dale Phurrough https://github.com/diablodale/nanodimmer
 *	email: erocmail@gmail.com / ccheng@aeon-labs.com (Modified Code) / dale@hidale.com (Modified Code)
 *	Copyright: Eric Maycock, Dale Phurrough
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
	definition (name: "Aeotec Inc Nano Dimmer", namespace: "diablodale", author: "Dale Phurrough") {
		capability "Actuator"
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
		capability "Sensor"
        capability "Polling"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Button"
        capability "Health Check"

        attribute  "configureOpportunity", "string"
        attribute  "firmware", "string"

        command "configure"

        fingerprint mfr: "0086", prod: "0003", model: "006F"
        fingerprint mfr: "006A", prod: "0003", model: "006F"
		fingerprint deviceId: "0x1101", inClusters: "0x5E,0x25,0x27,0x32,0x81,0x71,0x2C,0x2B,0x70,0x86,0x72,0x73,0x85,0x59,0x98,0x7A,0x5A"
	}

    preferences {
        input (
            title: "Settings",
            description: "The corner of the \"configuration\" tile will be orange if configuration is not complete. Press the tile to finish configuration.",
            displayDuringSetup: false,
            type: "paragraph",
            element: "paragraph"
        )
        generate_preferences(configuration_model())
    }

	simulator {

	}

	tiles(scale: 2){
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
	    }
		valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} kWh'
		}
        valueTile("wireLoad", "wireLoad", decoration: "flat", width: 2, height: 2) {
			state "val", label:'${currentValue}', defaultState: true
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("configure", "device.configureOpportunity", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "true",  label:'', action:"configure", icon:"https://github.com/erocm123/SmartThingsPublic/raw/master/devicetypes/erocm123/qubino-flush-1d-relay.src/configure@2x.png", defaultState: true
            state "false", label:'', action:"configure", icon:"st.secondary.configure"
        }
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'reset kWh', action:"reset"
		}

		main "switch"
		details (["switch", "power", "energy", "wireLoad", "refresh", "configure", "reset"])
	}
}

def parse(String description) {
    def result = null
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText: description, displayed: true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x84: 1, 0x98: 1, 0x56: 1, 0x60: 3])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    logging(cmd)
    dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	logging(cmd)
	dimmerEvents(cmd)
}

def dimmerEvents(physicalgraph.zwave.Command cmd) {
	def result = []
	def switchEvent = createEvent(name: "switch", value: cmd.value ? "on" : "off", displayed: true)
	result << switchEvent

    // multilevel switch cmd.value can be from 0-99 to represent fully off -> fully on
    // therefore "dimming levels" for a light are 1-98
    // App UI will display them as 1-98% with 99% never seen in the UI
    // 0% dim is the same as completely off, therefore don't update UI to persist the device's saved dimmer level
    def levelEvent
    if (cmd.value > 0) {
        def scaledValue = cmd.value > 98 ? 100 : cmd.value
        levelEvent = createEvent(name: "level", value: scaledValue, unit:"%",
            descriptionText:"${device.displayName} dimmed ${scaledValue}%",
            displayed: true)
        result << levelEvent
    }

    // if switch transitioned on/off or changed dim level then query device for wattage power after 3 seconds
    if (switchEvent.isStateChange || levelEvent?.isStateChange) {
		result << response(["delay 3000", zwave.meterV2.meterGet(scale: 2).format()])
	}
    result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
	logging("AssociationReport $cmd")
    state."association${cmd.groupingIdentifier}" = cmd.nodeId[0]
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x31: 2, 0x32: 3, 0x70: 1])
	if (encapsulatedCommand) {
		def result = zwaveEvent(encapsulatedCommand)
		result = result.collect {
			if (it instanceof physicalgraph.device.HubAction && !it.toString().startsWith("9881")) {
				response(cmd.CMD + "00" + it.toString())
			} else {
				it
			}
		}
		result
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    logging("Unhandled Z-Wave Event: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	logging(cmd)
	if (cmd.meterType == 1) {
        def map = [ displayed: true ]
        map << ([
            [ name: "energy", unit: "kWh", value: cmd.scaledMeterValue ],
            [ name: "energy", unit: "kVAh", value: cmd.scaledMeterValue ],
            [ name: "power", unit: "W", value: Math.round(cmd.scaledMeterValue)],
            [ name: "pulse count", unit: "pulses", value: cmd.scaledMeterValue ],
            [ name: "voltage", unit: "V", value: Math.round(cmd.scaledMeterValue) ],
            [ name: "current", unit: "A", value: cmd.scaledMeterValue],
            [ name: "power factor", unit: "R/Z", value: cmd.scaledMeterValue],
        ][cmd.scale] ?: [ name: "electric" ])
        return createEvent(map)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd){
    logging(cmd)
	def map = [displayed: true]
	switch (cmd.sensorType) {
		case 4:
			map.name = "power"
            map.unit = cmd.scale == 1 ? "Btu/h" : "W"
            map.value = Math.round(cmd.scaledSensorValue)
            break
        case 0xF:
			map.name = "voltage"
            map.unit = cmd.scale == 1 ? "mV" : "V"
            map.value = Math.round(cmd.scaledSensorValue)
		case 0x10:
			map.name = "current"
			map.unit = cmd.scale == 1 ? "mA" : "A"
            if (cmd.scale == 1)
                map.value = Math.round(cmd.scaledSensorValue)
            else
                map.value = cmd.scaledSensorValue
			break
		default:
			map.descriptionText = cmd.toString()
	}
	createEvent(map)
}

def on() {
	commands([zwave.basicV1.basicSet(value: 0xFF), zwave.basicV1.basicGet()])
}

def off() {
	commands([zwave.basicV1.basicSet(value: 0x00), zwave.basicV1.basicGet()])
}

def refresh() {
   	logging("$device.displayName refresh()")
    if (state.lastRefresh != null && now() - state.lastRefresh < 3000) {
        logging("Refresh Double Press - forced query of all known device parameters")
        def configuration = parseXml(configuration_model())
        def cmds = []
        configuration.Value.each
        {
            if ( "${it.@setting_type}" == "zwave" ) {
                cmds << zwave.configurationV1.configurationGet(parameterNumber: "${it.@index}".toInteger())
            }
        }
        cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
        state.lastRefresh = now()
        commands(cmds)
    }
    else {
        state.lastRefresh = now()
        forcedPing()
    }
}

def ping() {
   	logging("$device.displayName ping()")
    forcedPing()
}

def forcedPing() {
    def cmds = []
    cmds << zwave.meterV2.meterGet(scale: 0)
    cmds << zwave.meterV2.meterGet(scale: 2)
	cmds << zwave.basicV1.basicGet()
    commands(cmds)
}

def setLevel(level) {
	if (level > 99) level = 99
    if (level < 1) level = 1 // allows for quick "lowest dim level" in the app UI since it doesn't support the range(..) attribute
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: level)
    cmds << zwave.switchMultilevelV1.switchMultilevelGet()
	commands(cmds)
}

private command(physicalgraph.zwave.Command cmd) {
	if (state.sec) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=1500) {
	delayBetween(commands.collect{ command(it) }, delay)
}

def generate_preferences(configuration_model)
{
    def configuration = parseXml(configuration_model)
    configuration.Value.each
    {
        if (!it.@readonly.isEmpty() && it.@readonly.toBoolean()) {
            return;
        }
        def description = it.Help.isEmpty() ? [] : [it.Help.toString().replaceAll("[ \t]*[\n\r]+[ \t]*", "\n").trim()]
        def range = (it.@min != null && it.@max != null) ? "${it.@min}..${it.@max}" : null

        if (!it.@value.isEmpty()) {
            if (it.@type == "list") {
                description << "Default: " + it.Item.find {opt -> opt.@value == it.@value}?.@label
            }
            else {
                description << "Default: " + it.@value.toString()
            }
        }
        description = "${it.@label}" + (description.size() ? " ----------\n" + description.join("\n") : "")
        def displayDuringSetup = it.@displayDuringSetup.isEmpty() ? false : it.@displayDuringSetup.toBoolean()
        switch (it.@type)
        {
            case ["byte","short","four"]:
                input (
                    name: "${it.@index}",
                    title: description,
                    type: "number",
                    range: range,
                    defaultValue: it.@value.isEmpty() ? null : "${it.@value.toInteger()}",  // must provide string due to SmartThings bug to display decimal number instead
                    displayDuringSetup: displayDuringSetup
                )
                break
            case "list":
                def items = []
                it.Item.each { opt -> items << ["${opt.@value}":"${opt.@label}"] }
                input (
                    name: "${it.@index}",
                    title: description,
                    type: "enum",
                    options: items,
                    defaultValue: it.@value.isEmpty() ? null : "${it.@value}",
                    displayDuringSetup: displayDuringSetup
                )
                break
            case "decimal":
                input (
                    name: "${it.@index}",
                    title: description,
                    type: "decimal",
                    range: range,
                    defaultValue: it.@value.isEmpty() ? null : it.@value.toFloat(),
                    displayDuringSetup: displayDuringSetup
                )
                break
            case "boolean":
                input (
                    name: "${it.@index}",
                    title: description,
                    type: "bool",
                    defaultValue: it.@value.isEmpty() ? null : it.@value.toBoolean(),
                    displayDuringSetup: displayDuringSetup
                )
                break
        }
    }
}

def update_tiles() {
    def cachedDeviceParameters = state.cachedDeviceParameters ?: [:]
    def wireLoadText = ""
    if (cachedDeviceParameters?."128" != null) {
        def wireValue = convertParam(128, cmd2Integer(cachedDeviceParameters?."128"))
        if (wireValue > 0) wireLoadText = (wireValue == 1 ? "2-wire\n" : "3-wire\n")
    }
    if (cachedDeviceParameters?."130" != null) {
        def loadValue = convertParam(130, cmd2Integer(cachedDeviceParameters?."130"))
        if (loadValue > 0) wireLoadText = wireLoadText + (loadValue == 1 ? "Resistive\n" : loadValue == 2 ? "Capacitive\n" : "Inductive\n")
    }
    if (cachedDeviceParameters?."129" != null) {
        def edgeValue = convertParam(129, cmd2Integer(cachedDeviceParameters?."129"))
        wireLoadText = wireLoadText + (edgeValue == 0 ? "Trailing-edge" : "Leading-edge")
    }
    sendEvent(name: "wireLoad", value: wireLoadText.trim(), displayed: false, isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
     update_cached_device_parameters(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
     update_cached_device_parameters(cmd)
}

def update_cached_device_parameters(cmd)
{
    //logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is currently set to '${cmd2Integer(cmd.configurationValue)}'")
    def cmdParamNum = "${cmd.parameterNumber}".toInteger()
    def cmdParamValue = cmd2Integer(cmd.configurationValue)
    def cachedDeviceParameters = state.cachedDeviceParameters ?: [:]
    cachedDeviceParameters."${cmd.parameterNumber}" = cmd.configurationValue

    def configuration = parseXml(configuration_model())
    def matched_configuration_model = configuration.Value
        .find {it.@index.toInteger() == cmdParamNum}
    def readonly_parameter = false
    if (!matched_configuration_model.@readonly.isEmpty() && matched_configuration_model.@readonly.toBoolean()) {
        readonly_parameter = true
    }

    if ((readonly_parameter == false) && (settings."${cmd.parameterNumber}" != null))
    {
        if (coerceToInteger(settings."${cmd.parameterNumber}") == convertParam(cmdParamNum, cmdParamValue))
        {
            //setConfigureOpportunity(false)
        }
        else
        {
            // param value retrieved from device doesn't match user-chosen setting
            setConfigureOpportunity(true)
        }
    }

    state.cachedDeviceParameters = cachedDeviceParameters
    if ([128, 129, 130].contains(cmdParamNum)) {
        update_tiles()
    }
}

// Doesn't catch edge cases of changing assocations/parameters outside this device handler
def update_settings_on_device()
{
    logging("Nano Dimmer called update_settings_on_device()")
    def cmds = []
    def cachedDeviceParameters = state.cachedDeviceParameters ?: [:]

    def configuration = parseXml(configuration_model())
    def configureOpportunity = false

    if(!state?.needfwUpdate) {
       logging("Requesting device firmware version")
       cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
    }
    if (state?.association1 != zwaveHubNodeId) {
        cmds << zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId)
        cmds << zwave.associationV1.associationGet(groupingIdentifier: 1)
    }
    if (state?.association2 != zwaveHubNodeId) {
        cmds << zwave.associationV1.associationSet(groupingIdentifier: 2, nodeId: zwaveHubNodeId)
        cmds << zwave.associationV1.associationGet(groupingIdentifier: 2)
    }

    configuration.Value.each
    {
        if ("${it.@setting_type}" == "zwave") {
            if (cachedDeviceParameters."${it.@index}" == null)
            {
                configureOpportunity = true
                //logging("Device parameter ${it.@index} is unknown; requesting current value from device")
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
            else if (!it.@readonly.isEmpty() && it.@readonly.toBoolean()) {
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
            else if (settings."${it.@index}" != null && (convertParam(it.@index.toInteger(), cmd2Integer(cachedDeviceParameters."${it.@index}")) != coerceToInteger(settings."${it.@index}")))
            {
                //logging("Device parameter ${it.@index} will be updated to " + settings."${it.@index}")
                //configureOpportunity = true
                def convertedConfigurationValue = convertParam(it.@index.toInteger(), coerceToInteger(settings."${it.@index}"))
                cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
        }
    }

    setConfigureOpportunity(configureOpportunity)
    return cmds
}

def coerceToInteger(candidate) {
    if (candidate instanceof Boolean)
        return candidate ? 1 : 0
    return candidate.toInteger()
}

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) {
    switch(array.size()) {
	case 1:
		array[0]
    break
	case 2:
    	((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
    break
    case 3:
    	((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
    break
	case 4:
    	((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
	break
    }
}

def integer2Cmd(value, size) {
	switch(size) {
        case 1:
            [value]
            break
        case 2:
            def short value1   = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            [value2, value1]
            break
        case 3:
            def short value1   = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            def short value3 = (value >> 16) & 0xFF
            [value3, value2, value1]
            break
        case 4:
            def short value1 = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            def short value3 = (value >> 16) & 0xFF
            def short value4 = (value >> 24) & 0xFF
            [value4, value3, value2, value1]
            break
	}
}

def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd){
    logging("Firmware Report ${cmd.toString()}")
    def firmwareVersion
    switch(cmd.checksum) {
        case 19369:    // 0x4BA9
            firmwareVersion = "v2.02"
            break
        default:
            firmwareVersion = "v????"
    }
    firmwareVersion = firmwareVersion + " with checksum " + String.format("%04X", cmd.checksum)
    state.needfwUpdate = false
    createEvent(name: "firmware", value: firmwareVersion, displayed: false)
}

def installed() {
    log.debug "Nano Dimmer called installed()"
    migrate()
    def zwaveInfo = getZwaveInfo()  // https://community.smartthings.com/t/new-z-wave-fingerprint-format/48204/39
    if (zwaveInfo?.zw?.endsWith("s")) {
        // device was included securely
        logging("Nano Dimmer included securely")
        state.sec = 1
    }
    else {
        logging("Nano Dimmer included non-securely")
        state.sec = 0
    }
    // Device-Watch simply pings if no device events received for 122min (7320 seconds)
    sendEvent(name: "checkInterval", value: 7320, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def migrate() {
    // stop using undocumented DataValue storage; use instead state and device attributes
    if (getDataValue("firmware") != null) {
        updateDataValue("firmware", null)   // no known remove/delete
    }
    state.clear()
}

// Removed capability.configuration in metadata because it was causing duplicate calls since it called configure() and then
// core SmartThings called update(). Easy fix was to remove capability.configuration and instead just create a custom command
def configure() {
    logging("Nano Dimmer called configure()")
    def cmds = update_settings_on_device()
    commands(cmds)
}

// called after the app settings/preferences page is saved *and* on first install of device handler to device
def updated()
{
    logging("Nano Dimmer called updated()")
    response(configure())
}

// accepts param of boolean: true or false
def setConfigureOpportunity(configure) {
    if (configure != hasConfigureOpportunity()) {
        sendEvent(name:"configureOpportunity", value:(configure ? "true" : "false"), displayed: false, isStateChange: true)
    }
}

def hasConfigureOpportunity() {
    device.currentValue("configureOpportunity") == "true"
}

// BUGBUG param 131 (min dim setting) does not correspond to the non modified power level.
// e.g. I wanted 48-99 as my range. When I set 131=48, the dimming was almost imperceptable
// I had to set 131=22 to get the effect I wanted. Strange...is there a power of 2
// bug here? Because 48 / 2 is 24 which is ~22
def convertParam(number, value) {
	switch (number){
        /*
    	case 201:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        */
        default:
        	value
    }
}

private def logging(message) {
    if (settings == null || settings?.enableDebugging == null || settings?.enableDebugging == "true") log.debug "$message"
}


def configuration_model()
{
'''
<configuration>
    <Value type="boolean" byteSize="1" index="4" label="Overheat Protection" min="0" max="1" value="false" setting_type="zwave" fw="">
        <Help>
            Turn off after 30 seconds if temperature is over 100 C
        </Help>
    </Value>
    <Value type="list" byteSize="1" index="20" label="After Power Loss" min="0" max="2" value="0" setting_type="zwave" fw="">
        <Help>
            When power is lost, then restored, the dimmer is...
        </Help>
        <Item label="Same as before loss" value="0" />
        <Item label="Always on" value="1" />
        <Item label="Always off" value="2" />
    </Value>

    <Value type="list" byteSize="1" index="80" label="Instant Updates" min="0" max="4" value="3" setting_type="zwave" fw="">
        <Help>
            Type of report sent to association group 1 when dimmer changes. Used to instantly update dimmer status on gateway
        </Help>
        <Item label="None" value="0" />
        <Item label="Hail (outdated, more bandwidth)" value="1" />
        <Item label="Basic (simple on/off)" value="2" />
        <Item label="Multilevel (for dimmers)" value="3" />
        <Item label="Hail with external switch" value="4" />
    </Value>
    <Value type="boolean" byteSize="1" index="90" label="Power Usage Report" min="0" max="1" value="false" setting_type="zwave" fw="">
        <Help>
            Enable the two power usage reports (below)
        </Help>
    </Value>
    <Value type="byte" byteSize="2" index="91" label="Power Usage (watts)" min="0" max="60000" value="25" setting_type="zwave" fw="">
        <Help>
            Send report when power usage (watts) changes more than...
        </Help>
    </Value>
    <Value type="byte" byteSize="1" index="92" label="Power Usage (%)" min="0" max="100" value="5" setting_type="zwave" fw="">
        <Help>
            Send report when power usage (percentage) changes more than...
        </Help>
    </Value>

    <Value type="byte" byteSize="4" index="101" label="Timed Report 1" min="0" max="255" value="12" setting_type="zwave" fw="">
        <Help>
            Includes data:
            1 = Voltage
            2 = Current
            4 = Watt
            8 = kWh
            Example: If you want only Watt and kWh in this report, sum the values together 8+4=12
        </Help>
    </Value>
    <Value type="byte" byteSize="4" index="111" label="Timed Report 1 Schedule" min="1" max="2147483647" value="240" setting_type="zwave" fw="">
        <Help>
            Delay in seconds between each report
        </Help>
    </Value>
    <Value type="byte" byteSize="4" index="102" label="Timed Report 2" min="0" max="255" value="0" setting_type="zwave" fw="">
        <Help>
            Includes data:
            1 = Voltage
            2 = Current
            4 = Watt
            8 = kWh
            Example: If you want only voltage and current in this report, sum the values together 1+2=3
        </Help>
    </Value>
    <Value type="byte" byteSize="4" index="112" label="Timed Report 2 Schedule" min="1" max="2147483647" value="3600" setting_type="zwave" fw="">
        <Help>
            Delay in seconds between each report
        </Help>
    </Value>
    <Value type="byte" byteSize="4" index="103" label="Timed Report 3" min="0" max="255" value="0" setting_type="zwave" fw="">
        <Help>
            Includes data:
            1 = Voltage
            2 = Current
            4 = Watt
            8 = kWh
            Example: If you want all data, sum the values together 1+2+4+8=15
        </Help>
    </Value>
    <Value type="byte" byteSize="4" index="113" label="Timed Report 3 Schedule" min="1" max="2147483647" value="3600" setting_type="zwave" fw="">
        <Help>
            Delay in seconds between each report
        </Help>
    </Value>

    <Value type="list" byteSize="1" index="120" label="S1 Switch Type" min="0" max="4" value="0" setting_type="zwave" fw="">
        <Help>
            External S1 switch type...
            Note: Not reset after exclusion
        </Help>
        <Item label="Unidentified" value="0" />
        <Item label="2-state switch" value="1" />
        <Item label="3-way switch" value="2" />
        <Item label="Momentary push button" value="3" />
        <Item label="Automatic detection" value="4" />
    </Value>
    <Value type="list" byteSize="1" index="123" label="S1 Switch Control" min="1" max="3" value="3" setting_type="zwave" fw="">
        <Help>
            External switch S1 controls...
        </Help>
        <Item label="Dimmer itself" value="1" />
        <Item label="Devices in association group 3" value="2" />
        <Item label="Dimmer itself and devices in association group 3" value="3" />
    </Value>
    <Value type="boolean" byteSize="1" index="81" label="S1 Switch Notification" min="0" max="1" value="true" setting_type="zwave" fw="">
        <Help>
            External S1 switch sends Basic Set CC to association group 3
        </Help>
    </Value>
    <Value type="list" byteSize="1" index="121" label="S2 Switch Type" min="0" max="4" value="0" setting_type="zwave" fw="">
        <Help>
            External S2 switch type...
            Note: Not reset after exclusion
        </Help>
        <Item label="Unidentified" value="0" />
        <Item label="2-state switch" value="1" />
        <Item label="3-way switch" value="2" />
        <Item label="Momentary push button" value="3" />
        <Item label="Automatic detection" value="4" />
    </Value>
    <Value type="list" byteSize="1" index="124" label="S2 Switch Control" min="1" max="3" value="3" setting_type="zwave" fw="">
        <Help>
            External switch S2 controls...
        </Help>
        <Item label="Dimmer itself" value="1" />
        <Item label="Devices in association group 4" value="2" />
        <Item label="Dimmer itself and devices in association group 4" value="3" />
    </Value>
    <Value type="boolean" byteSize="1" index="82" label="S2 Switch Notification" min="0" max="1" value="true" setting_type="zwave" fw="">
        <Help>
            External S2 switch sends Basic Set CC to association group 4
        </Help>
    </Value>

    <Value type="byte" byteSize="1" index="125" label="Dimming speed" min="1" max="255" value="3" setting_type="zwave" fw="">
        <Help>
            Dimming speed in seconds
        </Help>
    </Value>
    <Value type="byte" byteSize="1" index="131" label="Dimming Minimum" min="0" max="99" value="0" setting_type="zwave" fw="">
        <Help>
            Minimum dimmer range
            Note: Not reset after exclusion
            Range: 0~99
        </Help>
    </Value>
    <Value type="byte" byteSize="1" index="132" label="Dimming Maximum" min="0" max="99" value="99" setting_type="zwave" fw="">
        <Help>
            Maximum dimmer range
            Note: Not reset after exclusion
            Range: 0~99
        </Help>
    </Value>
    <Value type="list" byteSize="1" index="129" label="Dimming mode" min="0" max="1" setting_type="zwave" fw="">
        <Help>
            Override the dimming mode to be trailing- or leading-edge
            Note: Not reset after exclusion
        </Help>
        <Item label="Trailing-edge (LEDs)" value="0" />
        <Item label="Leading-edge (legacy loads)" value="1" />
    </Value>

    <Value type="list" byteSize="1" index="128" label="Wiring Mode" min="0" max="2" value="0" setting_type="zwave" fw="" readonly="true">
        <Help>
            Auto-detected wiring mode
            Note: Not reset after exclusion
        </Help>
        <Item label="Unknown" value="0" />
        <Item label="2-wire mode" value="1" />
        <Item label="3-wire mode" value="2" />
    </Value>
    <Value type="list" byteSize="1" index="130" label="Load Type" min="0" max="3" value="0" setting_type="zwave" fw="" readonly="true">
        <Help>
            Auto-detected load type
            Note: Not reset after exclusion
        </Help>
        <Item label="Unknown" value="0" />
        <Item label="Resistive load" value="1" />
        <Item label="Capacitive load" value="2" />
        <Item label="Inductive load" value="3" />
    </Value>
    <Value type="list" byteSize="1" index="249" label="Automatic Load Detection" min="0" max="2" value="2" setting_type="zwave" fw="">
        <Help>
            Automatic dimmer load and wiring detection is...
        </Help>
        <Item label="Disabled" value="0" />
        <Item label="Detected only first time power is restored to dimmer" value="1" />
        <Item label="Always detected when power is restored to dimmer" value="2" />
    </Value>
</configuration>
'''
}

// TODO need to reconcile params and their meaning. The Engineering spec doc (revision 9) has significant differences
// for example, param 0x7a
// Engineering writes "Get the state of touch panel port"
// yet https://aeotec.freshdesk.com/support/solutions/articles/6000198943-how-to-update-nano-dimmer-z-wave-firmware-
// writes 0x7a was used to set control for S1 and S2 before firmware 2.02, and then changes it further in 2.02
// reading through both docs, I see that for the intention of "control of S1 and S2"
// engineering has 0x7b and 0x7c
// yet same webpage above for firmware 2.02 has 0x7a and 0x7b
// TODO Add in parameters at later time: 85, 86
//      Intentionally left out: 122, 252, 255
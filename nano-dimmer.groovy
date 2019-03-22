/**
 *
 *  Nano Dimmer (Aeotec Inc)
 *
 *	github: Eric Maycock (erocm123) -> Converted to Nano Dimmer custom device handler
 *	email: erocmail@gmail.com / ccheng@aeon-labs.com (Modified Code)
 *	Date: 2018-01-02 3:07PM
 *	Copyright Eric Maycock
 *
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
	definition (name: "Aeotec Inc Nano Dimmer", namespace: "erocm123", author: "Eric Maycock") {
		capability "Actuator"
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
		capability "Configuration"
		capability "Sensor"
        capability "Polling"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Button"
        capability "Health Check"

        attribute   "needUpdate", "string"

        fingerprint mfr: "0086", prod: "0103", model: "006F"
		fingerprint deviceId: "0x1101", inClusters: "0x5E,0x25,0x27,0x32,0x81,0x71,0x2C,0x2B,0x70,0x86,0x72,0x73,0x85,0x59,0x98,0x7A,0x5A"

	}

    preferences {
        input description: "Once you change values on this page, the corner of the \"configuration\" icon will change orange until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
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
            tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
           		attributeState "statusText", label:'${currentValue}'
            }
	    }
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} kWh'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'reset kWh', action:"reset"
		}
        standardTile("configure", "device.needUpdate", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "NO" , label:'', action:"configuration.configure", icon:"st.secondary.configure"
            state "YES", label:'', action:"configuration.configure", icon:"https://github.com/erocm123/SmartThingsPublic/raw/master/devicetypes/erocm123/qubino-flush-1d-relay.src/configure@2x.png"
        }


		main "switch"
		details (["switch", "power", "energy", "refresh", "configure", "reset"])
	}
}

def parse(String description) {
	def result = null
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description, isStateChange:true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x84: 1, 0x98: 1, 0x56: 1, 0x60: 3])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}

    def statusTextmsg = ""
    if (device.currentState('power') && device.currentState('energy')) statusTextmsg = "${device.currentState('power').value} W ${device.currentState('energy').value} kWh"
    sendEvent(name:"statusText", value:statusTextmsg, displayed:false)

	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    logging("BasicReport: $cmd")
    def events = []
	if (cmd.value == 0) {
		events << createEvent(name: "switch", value: "off")
	} else if (cmd.value == 255) {
		events << createEvent(name: "switch", value: "on")
	} else {
		events << createEvent(name: "switch", value: "on")
        events << createEvent(name: "switchLevel", value: cmd.value)
	}

    def request = update_needed_settings()

    if(request != []){
        return [response(commands(request)), events]
    } else {
        return events
    }
}

def buttonEvent(button, value) {
    logging("buttonEvent() Button:$button, Value:$value")
	createEvent(name: "button", value: value, data: [buttonNumber: button], descriptionText: "$device.displayName button $button was $value", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	logging(cmd)
	dimmerEvents(cmd)
}

def dimmerEvents(physicalgraph.zwave.Command cmd) {
	logging(cmd)
	def result = []
	def value = (cmd.value ? "on" : "off")
	def switchEvent = createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
	result << switchEvent
	if (cmd.value) {
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	}
	if (switchEvent.isStateChange) {
		result << response(["delay 3000", zwave.meterV2.meterGet(scale: 2).format()])
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
	logging("AssociationReport $cmd")
    state."association${cmd.groupingIdentifier}" = cmd.nodeId[0]
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x31: 2, 0x32: 3, 0x70: 1])
	if (encapsulatedCommand) {
		state.sec = 1
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
		if (cmd.scale == 0) {
        	//log.debug "kWh Returned"
			return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
		} else if (cmd.scale == 1) {
			return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
		} else if (cmd.scale == 2) {
        	//log.debug "Watt Returned"
			return createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
		} else {
			return createEvent(name: "electric", value: cmd.scaledMeterValue, unit: ["pulses", "V", "A", "R/Z", ""][cmd.scale - 3])
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd){
    logging("SensorMultilevelReport: $cmd")
	def map = [:]
	switch (cmd.sensorType) {
		case 4:
			map.name = "power"
            map.value = cmd.scaledSensorValue.toInteger().toString()
            map.unit = cmd.scale == 1 ? "Btu/h" : "W"
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

    def cmds = []
    if (state.lastRefresh != null && now() - state.lastRefresh < 5000) {
        logging("Refresh Double Press")
        def configuration = parseXml(configuration_model())
        configuration.Value.each
        {
            if ( "${it.@setting_type}" == "zwave" ) {
                cmds << zwave.configurationV1.configurationGet(parameterNumber: "${it.@index}".toInteger())
            }
        }
        cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
    } else {
        cmds << zwave.meterV2.meterGet(scale: 0)
        cmds << zwave.meterV2.meterGet(scale: 2)
	    cmds << zwave.basicV1.basicGet()
    }

    state.lastRefresh = now()

    commands(cmds)
}

def ping() {
   	logging("$device.displayName ping()")

    def cmds = []

    cmds << zwave.meterV2.meterGet(scale: 0)
    cmds << zwave.meterV2.meterGet(scale: 2)
	cmds << zwave.basicV1.basicGet()

    commands(cmds)
}

def setLevel(level) {
	if(level > 99) level = 99
    if(level < 1) level = 1
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: level)
    cmds << zwave.switchMultilevelV1.switchMultilevelGet()

	commands(cmds)
}

def updated()
{
    state.enableDebugging = settings.enableDebugging
    logging("updated() is being called")
    sendEvent(name: "checkInterval", value: 2 * 30 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    state.needfwUpdate = ""

    def cmds = update_needed_settings()

    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)

    response(commands(cmds))
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
        switch(it.@type)
        {
            case ["byte","short","four"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "boolean",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }
    }
}


def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]

    currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

    if (settings."${cmd.parameterNumber}" != null)
    {
        if (settings."${cmd.parameterNumber}".toInteger() == convertParam("${cmd.parameterNumber}".toInteger(), cmd2Integer(cmd.configurationValue)))
        {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
        }
        else
        {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
        }
    }

    state.currentProperties = currentProperties
}

def update_needed_settings()
{
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]

    def configuration = parseXml(configuration_model())
    def isUpdateNeeded = "NO"

    if(!state.needfwUpdate || state.needfwUpdate == ""){
       logging("Requesting device firmware version")
       cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
    }
    if(!state.association1 || state.association1 == "" || state.association1 == "1"){
       logging("Setting association group 1")
       cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)
       cmds << zwave.associationV1.associationGet(groupingIdentifier:1)
    }
    if(!state.association2 || state.association2 == "" || state.association1 == "2"){
       logging("Setting association group 2")
       cmds << zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)
       cmds << zwave.associationV1.associationGet(groupingIdentifier:2)
    }

    configuration.Value.each
    {
       if ("${it.@setting_type}" == "zwave"){
       //logging("Flag Placement")
            if (currentProperties."${it.@index}" == null)
            {
            //logging("Flag Placement") - Seems to fail here (FAIL)
                if (device.currentValue("currentFirmware") == null || "${it.@fw}".indexOf(device.currentValue("currentFirmware")) >= 0){
                    isUpdateNeeded = "YES"
                    logging("Current value of parameter ${it.@index} is unknown")
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            }
            else if (settings."${it.@index}" != null && convertParam(it.@index.toInteger(), cmd2Integer(currentProperties."${it.@index}")) != settings."${it.@index}".toInteger())
            {
            //logging("Flag Placement")
                //if (device.currentValue("currentFirmware") == null || "${it.@fw}".indexOf(device.currentValue("currentFirmware")) >= 0){
                    isUpdateNeeded = "YES"

                    logging("Parameter ${it.@index} will be updated to " + settings."${it.@index}")
                    def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger())
                    cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                //}
            }
        }
    }

    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
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

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
     update_current_properties(cmd)
     logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
     update_current_properties(cmd)
     logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
}

def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd){
    logging("Firmware Report ${cmd.toString()}")
    def firmwareVersion
    switch(cmd.checksum){
       case "3281":
          firmwareVersion = "3.08"
       break;
       default:
          firmwareVersion = cmd.checksum
    }
    state.needfwUpdate = "false"
    updateDataValue("firmware", firmwareVersion.toString())
    createEvent(name: "currentFirmware", value: firmwareVersion)
}

def configure() {
    state.enableDebugging = settings.enableDebugging
    logging("Configuring Device For SmartThings Use")
    def cmds = []

    cmds = update_needed_settings()

    if (cmds != []) commands(cmds)
    //else logging("Configuring Device Failed")
}

def convertParam(number, value) {
	switch (number){
    	case 201:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 202:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 203:
            if (value < 0)
            	65536 + value
        	else if (value > 1000)
            	value - 65536
            else
            	value
        break
        case 204:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        default:
        	value
        break
    }
}

private def logging(message) {
    if (state.enableDebugging == null || state.enableDebugging == "true") log.debug "$message"
}


def configuration_model()
{
'''
<configuration>
	  <Value type="list" byteSize="1" index="4" label="Overheat Protection." min="0" max="1" value="0" setting_type="zwave" fw="">
        <Help>
        The output load will automatically turn off after 30 seconds if temperature is over 100 C.
            0 - Disable
            1 - Enable
            Range: 0~1
            Default: 0 (Previous State)
        </Help>
            <Item label="Disable" value="0" />
            <Item label="Enable" value="1" />
      </Value>
      <Value type="list" byteSize="1" index="20" label="Dual Nano after power outage" min="0" max="2" value="0" setting_type="zwave" fw="">
        <Help>
        Configure the output load status after re-power from a power outage.
            0 - Last status before power outage.
            1 - Always ON
            2 - Always OFF
            Range: 0~2
            Default: 0 (Previous State)
        </Help>
            <Item label="Last Status" value="0" />
            <Item label="Always On" value="1" />
            <Item label="Always Off" value="2" />
      </Value>
      <Value type="list" byteSize="1" index="80" label="Instant Notification" min="0" max="4" value="0" setting_type="zwave" fw="">
        <Help>
        Notification report of status change sent to Group Assocation #1 when state of output load changes. Used to instantly update status to your gateway typically.
            0 - Nothing
            1 - Hail CC (uses more bandwidth)
            2 - Basic Report CC (ON/OFF style status report)
            3 - Multilevel Switch Report (Used for Dimmers status reports)
            4 - Hail CC when external switch is used to change status of either load.
            Range: 0~4
            Default: 0 (Previous State)
        </Help>
            <Item label="None" value="0" />
            <Item label="Hail CC" value="1" />
            <Item label="Basic Report CC" value="2" />
            <Item label="Multilevel Switch Report" value="3" />
            <Item label="Hail when External Switch used" value="4" />
      </Value>
      <Value type="list" byteSize="1" index="81" label="Notification send with S1 Switch" min="0" max="1" value="1" setting_type="zwave" fw="">
        <Help>
        To set which notification would be sent to the associated nodes in association group 3 when using the external switch 1 to switch the loads.
            0 = Send Nothing
            1 = Basic Set CC.
            Range: 0~1
            Default: 1 (Previous State)
        </Help>
            <Item label="Nothing" value="0" />
            <Item label="Basic Set CC" value="1" />
      </Value>
      <Value type="list" byteSize="1" index="82" label="Notification send with S2 Switch" min="0" max="1" value="1" setting_type="zwave" fw="">
        <Help>
        To set which notification would be sent to the associated nodes in association group 4 when using the external switch 2 to switch the loads.
            0 = Send Nothing
            1 = Basic Set CC.
            Range: 0~1
            Default: 1 (Previous State)
        </Help>
            <Item label="Nothing" value="0" />
            <Item label="Basic Set CC" value="1" />
      </Value>
      <Value type="list" byteSize="1" index="90" label="Threshold Enable/Disable" min="0" max="1" value="1" setting_type="zwave" fw="">
        <Help>
       		Enables/disables parameter 91 and 92 below:
            0 = disabled
            1 = enabled
            Range: 0~1
            Default: 1 (Previous State)
        </Help>
            <Item label="Disable" value="0" />
            <Item label="Enable" value="1" />
      </Value>
      <Value type="byte" byteSize="4" index="91" label="Watt Threshold" min="0" max="60000" value="25" setting_type="zwave" fw="">
        <Help>
       		The value here represents minimum change in wattage (in terms of wattage) for a REPORT to be sent (Valid values 0-60000).
            Range: 0~60000
            Default: 25 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="1" index="92" label="kWh Threshold" min="0" max="100" value="5" setting_type="zwave" fw="">
        <Help>
       		The value here represents minimum change in wattage percent (in terms of percentage %) for a REPORT to be sent.
            Range: 0~100
            Default: 5 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="101" label="(Group 1) Timed Automatic Reports" min="0" max="255" value="12" setting_type="zwave" fw="">
        <Help>
       		Sets the sensor report for kWh, Watt, Voltage, or Current.
            Value Identifiers-
                1 = Voltage
                2 = Current
                4 = Watt
                8 = kWh
            Example: If you want only Watt and kWh to report, sum the value identifiers together for Watt and kWh. 8 + 4 = 12, therefore entering 12 into this setting will give you Watt + kWh reports if set.
            Range: 0~255
            Default: 12 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="102" label="(Group 2) Timed Automatic Reports" min="0" max="255" value="0" setting_type="zwave" fw="">
        <Help>
       		Sets the sensor report for kWh, Watt, Voltage, or Current.
            Value Identifiers-
                1 = Voltage
                2 = Current
                4 = Watt
                8 = kWh
            Example: If you want only Voltage and Current to report, sum the value identifiers together for Voltage + Current. 1 + 2 = 3, therefore entering 3 into this setting will give you Voltage + Current reports if set.
            Range: 0~255
            Default: 0 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="103" label="(Group 3) Timed Automatic Reports" min="0" max="255" value="0" setting_type="zwave" fw="">
        <Help>
       		Sets the sensor report for kWh, Watt, Voltage, or Current.
            Value Identifiers-
                1 = Voltage
                2 = Current
                4 = Watt
                8 = kWh
            Example: If you want all values to report, sum the value identifiers together for Voltage + Current + Watt + kWh. 1 + 2 + 4 + 8 = 15, therefore entering 15 into this setting will give you Voltage + Current + Watt + kWh reports if set.
            Range: 0~255
            Default: 0 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="111" label="(Group 1) Set Report in Seconds" min="1" max="2147483647" value="240" setting_type="zwave" fw="">
        <Help>
       		Set the interval of automatic report for Report group 1 in (seconds). This controls (Group 1) Timed Automatic Reports.
            Range: 0~2147483647
            Default: 240 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="112" label="(Group 2) Set Report in Seconds" min="1" max="2147483647" value="3600" setting_type="zwave" fw="">
        <Help>
       		Set the interval of automatic report for Report group 2 in (seconds). This controls (Group 2) Timed Automatic Reports.
            Range: 0~2147483647
            Default: 3600 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="4" index="113" label="(Group 3) Set Report in Seconds" min="1" max="2147483647" value="3600" setting_type="zwave" fw="">
        <Help>
       		Set the interval of automatic report for Report group 3 in (seconds). This controls (Group 3) Timed Automatic Reports.
            Range: 0~2147483647
            Default: 3600 (Previous State)
        </Help>
      </Value>
      <Value type="list" byteSize="1" index="120" label="External Switch S1 Setting" min="0" max="4" value="0" setting_type="zwave" fw="">
        <Help>
        Configure the external switch mode for S1 via Configuration Set.
            0 = Unidentified mode.
            1 = 2-state switch mode.
            2 = 3-way switch mode.
            3 = momentary switch button mode.
            4 = Enter automatic identification mode. //can enter this mode by tapping internal button 4x times within 2 seconds.
            Note: When the mode is determined, this mode value will not be reset after exclusion.
            Range: 0~4
            Default: 0 (Previous State)
        </Help>
            <Item label="Unidentified" value="0" />
            <Item label="2-State Switch Mode" value="1" />
            <Item label="3-way Switch Mode" value="2" />
            <Item label="Momentary Push Button Mode" value="3" />
            <Item label="Automatic Identification" value="4" />
      </Value>
      <Value type="list" byteSize="1" index="121" label="External Switch S2 Setting" min="0" max="4" value="0" setting_type="zwave" fw="">
        <Help>
        Configure the external switch mode for S2 via Configuration Set.
            0 = Unidentified mode.
            1 = 2-state switch mode.
            2 = 3-way switch mode.
            3 = momentary switch button mode.
            4 = Enter automatic identification mode. //can enter this mode by tapping internal button 6x times within 2 seconds.
            Note: When the mode is determined, this mode value will not be reset after exclusion.
            Range: 0~4
            Default: 0 (Previous State)
        </Help>
            <Item label="Unidentified" value="0" />
            <Item label="2-State Switch Mode" value="1" />
            <Item label="3-way Switch Mode" value="2" />
            <Item label="Momentary Push Button Mode" value="3" />
            <Item label="Automatic Identification" value="4" />
      </Value>
      <Value type="list" byteSize="1" index="123" label="Control Destination of S1 (Group Association #3)" min="1" max="3" value="3" setting_type="zwave" fw="">
        <Help>
        Set the control destination for external switch S1 for Group Association #3
            1 = control the output loads of itself.
            2 = control the other nodes.
            3 = control the output loads of itself and other nodes.
            Range: 1~3
            Default: 0 (Previous State)
        </Help>
            <Item label="Control Load Only" value="1" />
            <Item label="Control other Nodes Only" value="2" />
            <Item label="Control Load and Other Nodes" value="3" />
      </Value>
      <Value type="list" byteSize="1" index="124" label="Control Destination of S2 (Group Association #4)" min="1" max="3" value="3" setting_type="zwave" fw="">
        <Help>
        Set the control destination for external switch S2 for Group Association #4
            1 = control the output loads of itself.
            2 = control the other nodes.
            3 = control the output loads of itself and other nodes.
            Range: 1~3
            Default: 0 (Previous State)
        </Help>
            <Item label="Control Load Only" value="1" />
            <Item label="Control other Nodes Only" value="2" />
            <Item label="Control Load and Other Nodes" value="3" />
      </Value>
      <Value type="byte" byteSize="1" index="125" label="Dimming Ramp Speed" min="1" max="255" value="3" setting_type="zwave" fw="">
        <Help>
       		Set the default dimming rate in seconds.
            Range: 1~255
            Default: 3 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="1" index="131" label="Minimum Dim Setting" min="0" max="99" value="0" setting_type="zwave" fw="">
        <Help>
       		Set the min brightness level that the load can reach to.
            Note: When the level is determined, this level value will not be reset after exclusion.
            Range: 0~99
            Default: 0 (Previous State)
        </Help>
      </Value>
      <Value type="byte" byteSize="1" index="132" label="Maximum Dim Setting" min="0" max="99" value="99" setting_type="zwave" fw="">
        <Help>
       		Set the max brightness level that the load can reach to.
            Note: When the level is determined, this level value will not be reset after exclusion.
            Range: 0~99
            Default: 99 (Previous State)
        </Help>
      </Value>
      <Value type="list" byteSize="1" index="249" label="Auto-load Detection" min="0" max="2" value="2" setting_type="zwave" fw="">
        <Help>
            Set the recognition way of load when Nano Dimmer is powered up from power outage or power cycle.
            0 = Never recognize the load when power on.
            1 = Only recognize once when first power on.
            2 = Recognize the load once power on.
            Range: 1~3
            Default: 0 (Previous State)
        </Help>
            <Item label="Never recognize the load when power on." value="0" />
            <Item label="Only recognize once when first power on." value="1" />
            <Item label="Recognize the load once power on. " value="2" />
      </Value>
</configuration>
'''
}

//add in parameter at later time:
	//85-86
//Left out:
	//122, 129-130, 252, 255
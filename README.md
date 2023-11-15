
## Plant Logger

An ArcGIS-enabled IoT device using a Raspberry Pi, the ArcGIS Maps SDK for Java, and ArcGIS Online. This device continually monitors houseplant health, encompassing aspects like soil moisture and growth.

## Hardware Configuration 

![raspberry pi 4 with camera, capacitive moisture sensor and gps module](https://github.com/PaulGibbs3rd/plant-logger/blob/main/rpi%20setup.jpg)

Raspberry Pi 4B (8GB RAM)
Raspberry Pi Camera with lens
Capacitive Moisture Sensor
GPS module

## How to run: 

1. Follow the set up instruction found in [Raspberry Pi Setup.md](https://github.com/PaulGibbs3rd/plant-logger/blob/main/Raspberry%20Pi%20Setup.md)
2. Git Clone https://github.com/PaulGibbs3rd/plant-logger into a new directory
3. Change into the directotory
4. Run the application via 'sudo ./gradlew run'

Sensor data will begin being listed as the device's location changes. 

## Resources

* [ArcGIS Maps SDK for Java](https://developers.arcgis.com/java/)  
* [ArcGIS Blog](https://www.esri.com/arcgis-blog/developers/)  
* [Esri Twitter](https://twitter.com/arcgisdevs)  

## Issues

Find a bug or want to request a new feature?  Please let us know by submitting an issue.

## Contributing

Esri welcomes contributions from anyone and everyone. Please see our [guidelines for contributing](https://github.com/esri/contributing).

## Licensing

Copyright 2023 Esri

Licensed under the Apache License, Version 2.0 (the "License"); you may not 
use this file except in compliance with the License. You may obtain a copy 
of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
License for the specific language governing permissions and limitations 
under the License.

A copy of the license is available in the repository's license.txt file.

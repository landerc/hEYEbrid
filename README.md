# hEYEbrid Calibration-Free and Mobile Eye tracking

## hEYEbrid Android App
Android application to use the head-mounted device as described in (https://dl.acm.org/citation.cfm?id=3161166&dl=ACM&coll=DL).
The basic application is licensed unde rthe Apache License, Version 2.0.

##### The application has several dependencises:
* UVCCamera (https://github.com/saki4510t/UVCCamera) used to connect USB cameras

  We did slight modifications within this library.
  
* OpenCV 3.2

  We used openCv for image processing.

* fontinator (https://github.com/svendvd/Fontinator/blob/master/LICENSE.md)

  We used this library to add custom fonts.
  
* ExpandableLayout (https://github.com/AAkira/ExpandableLayout)

* WheelView (https://github.com/BlackBoxVision/material-wheel-view)

## Hardware
The head-mounted device is based on Pupil Lab's eye tracking device. We used the glasses frame and removed the cameras.
We printed our own camera enclosure to mount two cameras in front of the eye. The model can be found in the folder hardware. We printed the mount using a FormLabs 3D pribnter.

# hEYEbrid Calibration-Free and Mobile Eye tracking

## hEYEbrid Android App
Android application to use the head-mounted device as described in (https://dl.acm.org/citation.cfm?id=3161166&dl=ACM&coll=DL).
The basic application is licensed unde rthe Apache License, Version 2.0. IMPORTANT: The app is based on Gradle 2.2.3 (we recommend not to update anything concerning gradle and build tools, as this might lead to build errors)

##### The application has several dependencises:
* UVCCamera (https://github.com/saki4510t/UVCCamera) used to connect USB cameras

  We did slight modifications within this library.
  
* OpenCV 3.2 (ADAPT THE BUILD PATH IN Android.mk)

  We used openCv for image processing.

* fontinator (https://github.com/svendvd/Fontinator/blob/master/LICENSE.md)

  We used this library to add custom fonts.
  
* ExpandableLayout (https://github.com/AAkira/ExpandableLayout)

* WheelView (https://github.com/BlackBoxVision/material-wheel-view)

* ELse for eye tracking (http://www.ti.uni-tuebingen.de/uploads/tx_timitarbeiter/ElSe.pdf)

## Usage
Build the software using Android Studio, latest version. IMPORTANT: Remove the current NDK and replace it by NDK 14b!

## Hardware
The head-mounted device is based on Pupil Lab's eye tracking device. We used the glasses frame and removed the cameras.
We printed our own camera enclosure to mount two cameras in front of the eye. The model can be found in the folder hardware. We printed the mount using a FormLabs 3D pribnter.

## Calibration
You have to do a one time calibration after asembling the two cameras to obtin the transformation matrix between both cameras.
We are using a homography matrix for the mapping between both cameras. We therefore provide a small c++ file in the folder calibration that can be executed to compute the transformation amtrix between two images. Use this file with images recorded for each of the two cameras. Note: Record the cameras when you finished assembling them.

The obtained transformation matrix has to be pasted into the file eyetracker.cpp of the main Android application (jni folder).

# Android.mk for Limelight's Evdev Reader
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

# evdev_reader is only needed for root builds (removed).
# Build is disabled in non-root builds.

# ifeq (root,$(PRODUCT_FLAVOR))
#     include $(CLEAR_VARS)
#     ...
# endif


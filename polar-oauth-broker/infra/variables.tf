variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region for Cloud Function deployment"
  type        = string
  default     = "us-central1"
}

variable "polar_client_id" {
  description = "Polar OAuth client ID"
  type        = string
}

variable "android_app_fingerprint" {
  description = "SHA-256 fingerprint of the Android signing certificate (for future App Links)"
  type        = string
  default     = ""
}

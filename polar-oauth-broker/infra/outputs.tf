output "function_url" {
  description = "Base URL of the deployed Cloud Function"
  value       = google_cloudfunctions2_function.polar_oauth_broker.service_config[0].uri
}

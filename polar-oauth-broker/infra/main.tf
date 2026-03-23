terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# ---------------------------------------------------------------------------
# Service Account
# ---------------------------------------------------------------------------

resource "google_service_account" "polar_oauth" {
  account_id   = "polar-oauth-broker"
  display_name = "Polar OAuth Broker"
  description  = "Service account for the Polar OAuth Cloud Function"
}

resource "google_project_iam_member" "datastore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.polar_oauth.email}"
}

resource "google_project_iam_member" "secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.polar_oauth.email}"
}

# ---------------------------------------------------------------------------
# Secret Manager
# ---------------------------------------------------------------------------

resource "google_secret_manager_secret" "polar_client_secret" {
  secret_id = "polar-client-secret"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "polar_session_key" {
  secret_id = "polar-oauth-session-key"
  replication {
    auto {}
  }
}

# Placeholder versions — replace with real values after initial apply
resource "google_secret_manager_secret_version" "polar_client_secret_v1" {
  secret      = google_secret_manager_secret.polar_client_secret.id
  secret_data = "REPLACE_WITH_REAL_CLIENT_SECRET"

  lifecycle {
    ignore_changes = [secret_data]
  }
}

resource "google_secret_manager_secret_version" "polar_session_key_v1" {
  secret      = google_secret_manager_secret.polar_session_key.id
  secret_data = "REPLACE_WITH_32_BYTE_HEX_KEY___"

  lifecycle {
    ignore_changes = [secret_data]
  }
}

# ---------------------------------------------------------------------------
# Firestore database + TTL policy
# ---------------------------------------------------------------------------

resource "google_firestore_database" "default" {
  project     = var.project_id
  name        = "(default)"
  location_id = var.region
  type        = "FIRESTORE_NATIVE"
}

resource "google_firestore_field" "oauth_sessions_ttl" {
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "oauth_sessions"
  field      = "expires_at"

  ttl_config {}

  index_config {}

  depends_on = [google_firestore_database.default]
}

# ---------------------------------------------------------------------------
# Cloud Function (gen2)
# ---------------------------------------------------------------------------

# Zip the source code for upload
data "archive_file" "source" {
  type        = "zip"
  source_dir  = "${path.module}/.."
  output_path = "${path.module}/tmp/source.zip"
  excludes    = ["infra", ".git", "__pycache__", "*.pyc"]
}

resource "google_storage_bucket" "source" {
  name                        = "${var.project_id}-polar-oauth-source"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true
}

resource "google_storage_bucket_object" "source" {
  name   = "polar-oauth-broker-${data.archive_file.source.output_md5}.zip"
  bucket = google_storage_bucket.source.name
  source = data.archive_file.source.output_path
}

resource "google_cloudfunctions2_function" "polar_oauth_broker" {
  name     = "polar-oauth-broker"
  location = var.region

  build_config {
    runtime     = "python312"
    entry_point = "handler"
    source {
      storage_source {
        bucket = google_storage_bucket.source.name
        object = google_storage_bucket_object.source.name
      }
    }
  }

  service_config {
    max_instance_count    = 10
    min_instance_count    = 0
    available_memory      = "256M"
    timeout_seconds       = 30
    service_account_email = google_service_account.polar_oauth.email

    environment_variables = {
      GCP_PROJECT    = var.project_id
      POLAR_CLIENT_ID = var.polar_client_id
      ANDROID_APP_FINGERPRINT = var.android_app_fingerprint
    }
  }

  depends_on = [
    google_project_iam_member.datastore_user,
    google_project_iam_member.secret_accessor,
  ]
}

# Allow unauthenticated invocation (Polar must reach the callback)
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloudfunctions2_function.polar_oauth_broker.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

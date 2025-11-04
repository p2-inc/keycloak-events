
resource "aws_iam_role" "firehose_data" {
  name = "firehose_data"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "firehose.amazonaws.com"
        }
      },
    ]
  })
}

resource "aws_iam_policy" "firehose_data" {
  name        = "firehose_data"
  path        = "/"
  description = "Firehose / Record data"

  # Terraform's "jsonencode" function converts a
  # Terraform expression result to valid JSON syntax.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:AbortMultipartUpload",
          "s3:GetBucketLocation",
          "s3:GetObject",
          "s3:ListBucket",
          "s3:ListBucketMultipartUploads",
          "s3:PutObject",
          "glue:GetTable",
          "glue:GetTableVersion",
          "glue:GetTableVersions"
        ]
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "firehose_data-attach" {
  role       = aws_iam_role.firehose_data.name
  policy_arn = aws_iam_policy.firehose_data.arn
}

# Create a database
resource "aws_glue_catalog_database" "keycloak-events" {
  name = "keycloak-events"
}

# Create an S3 Bucket
resource "aws_s3_bucket" "keycloak-events-data" {
  bucket = "keycloak-events-data"
}

# Set the bucket to private
resource "aws_s3_bucket" "keycloak-admin-events-data" {
  bucket = "keycloak-admin-events-data"
}

# declare a few local variables to make additional streams easier to create
locals {
  user_s3bucket     = aws_s3_bucket.keycloak-events-data
  user_stream_path  = "event-streams/user-events"
  admin_s3bucket    = aws_s3_bucket.keycloak-admin-events-data
  admin_stream_path = "event-streams/admin-events"
}

# Create a table
resource "aws_glue_catalog_table" "keycloak-events-user-events" {
  name          = "keycloak-events-user-events"
  database_name = aws_glue_catalog_database.keycloak-events.name

  table_type = "EXTERNAL_TABLE"

  parameters = {
    EXTERNAL                      = "TRUE"
    "parquet.compression"         = "SNAPPY"
    "projection.enabled"          = "true"
    "projection.dt.type"          = "date"
    "projection.dt.format"        = "yyyy-MM-dd"
    "projection.dt.range"         = "2020-01-01,NOW"
    "projection.dt.interval"      = "1"
    "projection.dt.interval.unit" = "DAYS"
    "projection.hour.type"        = "integer"
    "projection.hour.range"       = "0,23"
    "storage.location.template"   = "s3://${local.user_s3bucket.bucket}/${local.user_stream_path}/data/dt=$${dt}/hour=$${hour}/"
  }

  storage_descriptor {
    location      = "s3://${local.user_s3bucket.bucket}/${local.user_stream_path}/data/"
    input_format  = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat"

    ser_de_info {
      name                  = "my-stream"
      serialization_library = "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe"

      parameters = {
        "serialization.format" = 1
      }
    }

    # Table Schema
    columns {
      name = "id"
      type = "string"
    }

    columns {
      name = "client_id"
      type = "string"
    }

    columns {
      name = "details_json"
      type = "string"
    }

    columns {
      name = "error"
      type = "string"
    }

    columns {
      name = "ip_address"
      type = "string"
    }

    columns {
      name = "realm_id"
      type = "string"
    }

    columns {
      name = "session_id"
      type = "string"
    }

    columns {
      name = "event_time"
      type = "bigint"
    }

    columns {
      name = "type"
      type = "string"
    }

    columns {
      name = "user_id"
      type = "string"
    }

    columns {
      name = "details_json_long_value"
      type = "string"
    }
  }

  partition_keys {
    name = "dt"
    type = "string"
  }

  partition_keys {
    name = "hour"
    type = "int"
  }
}

# Create Firehose Stream
resource "aws_kinesis_firehose_delivery_stream" "keycloak-events-user-events" {
  name        = "keycloak-events-user-events"
  destination = "extended_s3"

  depends_on = [
    aws_iam_role_policy_attachment.firehose_data-attach
  ]

  extended_s3_configuration {
    role_arn   = aws_iam_role.firehose_data.arn
    bucket_arn = local.user_s3bucket.arn

    # Example prefix using partitionKeyFromQuery, applicable to JQ processor
    prefix              = "${local.user_stream_path}/data/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/"
    error_output_prefix = "${local.user_stream_path}/errors/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/!{firehose:error-output-type}/"

    # https://docs.aws.amazon.com/firehose/latest/dev/dynamic-partitioning.html
    buffering_size = 64
    buffering_interval = 600

    # ... other configuration ...
    data_format_conversion_configuration {
      input_format_configuration {
        deserializer {
          hive_json_ser_de {}
        }
      }

      output_format_configuration {
        serializer {
          parquet_ser_de {}
        }
      }

      schema_configuration {
        database_name = aws_glue_catalog_table.keycloak-events-user-events.database_name
        role_arn      = aws_iam_role.firehose_data.arn
        table_name    = aws_glue_catalog_table.keycloak-events-user-events.name
      }
    }
  }
}

resource "aws_glue_catalog_table" "keycloak-events-admin-events" {
  name          = "keycloak-events-admin-events"
  database_name = aws_glue_catalog_database.keycloak-events.name

  table_type = "EXTERNAL_TABLE"

  parameters = {
    EXTERNAL                      = "TRUE"
    "parquet.compression"         = "SNAPPY"
    "projection.enabled"          = "true"
    "projection.dt.type"          = "date"
    "projection.dt.format"        = "yyyy-MM-dd"
    "projection.dt.range"         = "2020-01-01,NOW"
    "projection.dt.interval"      = "1"
    "projection.dt.interval.unit" = "DAYS"
    "projection.hour.type"        = "integer"
    "projection.hour.range"       = "0,23"
    "storage.location.template"   = "s3://${local.admin_s3bucket.bucket}/${local.admin_stream_path}/data/dt=$${dt}/hour=$${hour}/"
  }

  storage_descriptor {
    location      = "s3://${local.admin_s3bucket.bucket}/${local.admin_stream_path}/data/"
    input_format  = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat"

    ser_de_info {
      name                  = "admin-events"
      serialization_library = "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe"

      parameters = {
        "serialization.format" = 1
      }
    }

    columns {
      name = "id"
      type = "string"
    }

    columns {
      name = "admin_event_time"
      type = "bigint"
    }

    columns {
      name = "realm_id"
      type = "string"
    }

    columns {
      name = "operation_type"
      type = "string"
    }

    columns {
      name = "auth_realm_id"
      type = "string"
    }

    columns {
      name = "auth_client_id"
      type = "string"
    }

    columns {
      name = "auth_user_id"
      type = "string"
    }

    columns {
      name = "ip_address"
      type = "string"
    }

    columns {
      name = "resource_path"
      type = "string"
    }

    columns {
      name = "representation"
      type = "string"
    }

    columns {
      name = "error"
      type = "string"
    }

    columns {
      name = "resource_type"
      type = "string"
    }

    columns {
      name = "details_json"
      type = "string"
    }
  }

  partition_keys {
    name = "dt"
    type = "string"
  }

  partition_keys {
    name = "hour"
    type = "int"
  }
}

resource "aws_kinesis_firehose_delivery_stream" "keycloak-events-admin-events" {
  name        = "keycloak-events-admin-events"
  destination = "extended_s3"

  depends_on = [
    aws_iam_role_policy_attachment.firehose_data-attach
  ]

  extended_s3_configuration {
    role_arn   = aws_iam_role.firehose_data.arn
    bucket_arn = local.admin_s3bucket.arn

    prefix              = "${local.admin_stream_path}/data/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/"
    error_output_prefix = "${local.admin_stream_path}/errors/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/!{firehose:error-output-type}/"

    buffering_size     = 64
    buffering_interval = 600

    data_format_conversion_configuration {
      input_format_configuration {
        deserializer {
          hive_json_ser_de {}
        }
      }

      output_format_configuration {
        serializer {
          parquet_ser_de {}
        }
      }

      schema_configuration {
        database_name = aws_glue_catalog_table.keycloak-events-admin-events.database_name
        role_arn      = aws_iam_role.firehose_data.arn
        table_name    = aws_glue_catalog_table.keycloak-events-admin-events.name
      }
    }
  }
}

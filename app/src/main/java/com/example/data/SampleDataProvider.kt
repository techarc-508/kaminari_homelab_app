package com.example.data

import com.example.model.NftyAction
import com.example.model.NftyAttachment
import com.example.model.NftyMessage
import com.example.model.NftyPriority
import com.example.model.NftyTopic
import com.example.model.TopicPermission

object SampleDataProvider {

    fun getSampleTopics(): List<NftyTopic> {
        val now = System.currentTimeMillis()
        return listOf(
            NftyTopic(
                name = "homelab-alerts",
                description = "Critical alerts from Proxmox VE, UPS, and thermal monitoring",
                messageCount = 6,
                isSubscribed = true,
                isPaused = false,
                lastActivityTime = now - 12 * 60 * 1000,
                permission = TopicPermission.AUTH_REQUIRED,
            ),
            NftyTopic(
                name = "backups",
                description = "Automated snapshot and off-site backup confirmations",
                messageCount = 4,
                isSubscribed = true,
                isPaused = false,
                lastActivityTime = now - 3 * 3600 * 1000,
                permission = TopicPermission.AUTH_REQUIRED,
            ),
            NftyTopic(
                name = "build-server",
                description = "Gitea Actions & CI/CD deployment notifications",
                messageCount = 3,
                isSubscribed = true,
                isPaused = false,
                lastActivityTime = now - 26 * 3600 * 1000,
                permission = TopicPermission.GUEST_READ,
            ),
            NftyTopic(
                name = "iot-telemetry",
                description = "Home Assistant environmental telemetry and power metrics",
                messageCount = 5,
                isSubscribed = true,
                isPaused = false,
                lastActivityTime = now - 45 * 60 * 1000,
                permission = TopicPermission.GUEST_READ,
            ),
            NftyTopic(
                name = "security",
                description = "SSH bastions, fail2ban triggers, and Traefik SSL renewals",
                messageCount = 2,
                isSubscribed = true,
                isPaused = false,
                lastActivityTime = now - 52 * 3600 * 1000,
                permission = TopicPermission.RESTRICTED,
            ),
        )
    }

    fun getSampleMessages(): List<NftyMessage> {
        val now = System.currentTimeMillis()
        val min = 60 * 1000L
        val hour = 3600 * 1000L
        val day = 24 * hour

        return listOf(
            NftyMessage(
                id = "msg_001",
                time = now - 12 * min,
                topic = "homelab-alerts",
                title = "UPS Battery Discharge: APC Smart-UPS 1500",
                message = "Main AC line power lost on rack-node-01. Switching to battery inverter. Estimated runtime: 34 minutes at 240W draw.",
                priority = NftyPriority.URGENT,
                tags = listOf("warning", "power", "ups", "hardware"),
                click = "https://nfty.rplab.qzz.io/ups-diagnostics",
                actions = listOf(
                    NftyAction("act_view", "view", "Inspect UPS Telemetry", "https://nfty.rplab.qzz.io/ups-diagnostics")
                ),
                isRead = false,
                isSaved = true,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_002",
                time = now - 45 * min,
                topic = "iot-telemetry",
                title = "Rack Ambient Temp Threshold Exceeded",
                message = "Intake sensor #2 reached 28.4°C (trigger: 27.5°C). Exhaust fan speed stepped up to 75% duty cycle via PWM controller.",
                priority = NftyPriority.HIGH,
                tags = listOf("thermometer", "temperature", "fan"),
                isRead = false,
                isSaved = false,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_003",
                time = now - 3 * hour,
                topic = "backups",
                title = "PostgreSQL Nightly Dump Complete",
                message = "Cluster db-primary (9 databases, 14.8 GB compressed) verified and synced to offsite S3 MinIO storage. MD5 checksum validated.",
                priority = NftyPriority.DEFAULT,
                tags = listOf("floppy_disk", "backup", "postgres", "verified"),
                attachment = NftyAttachment(
                    name = "backup-report-20261015.json",
                    url = "https://nfty.rplab.qzz.io/reports/backup-20261015.json",
                    type = "application/json",
                    size = 4280,
                ),
                isRead = true,
                isSaved = true,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_004",
                time = now - 5 * hour,
                topic = "homelab-alerts",
                title = "Proxmox VM 104 (matrix-synapse) Auto-Recovered",
                message = "Watchdog triggered kernel OOM restart. Process memory capped at 4096MB. Guest OS rebooted cleanly in 18.2s.",
                priority = NftyPriority.DEFAULT,
                tags = listOf("repeat", "proxmox", "linux"),
                isRead = true,
                isSaved = false,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_005",
                time = now - 18 * hour,
                topic = "build-server",
                title = "Pipeline #481 Succeeded: kaminari-gateway",
                message = "Build target arm64/v8 finished with zero errors. Image deployed to local registry: hub.rplab.internal/kaminari:2.4.1.",
                priority = NftyPriority.LOW,
                tags = listOf("white_check_mark", "docker", "ci-cd"),
                click = "https://nfty.rplab.qzz.io/builds/481",
                isRead = true,
                isSaved = false,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_006",
                time = now - 26 * hour,
                topic = "security",
                title = "Fail2ban: 12 IP addresses banned on port 22",
                message = "Brute force attack blocked on perimeter SSH bastion. GeoIP origin: ASN14061. Firewall ipset updated automatically.",
                priority = NftyPriority.HIGH,
                tags = listOf("shield", "security", "fail2ban"),
                isRead = true,
                isSaved = false,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_007",
                time = now - 32 * hour,
                topic = "security",
                title = "Traefik ACME: Let's Encrypt Certificate Renewed",
                message = "SAN wildcard *.rplab.qzz.io renewed successfully using Cloudflare DNS-01 challenge. Next expiry: 89 days.",
                priority = NftyPriority.LOW,
                tags = listOf("lock", "ssl", "traefik"),
                isRead = true,
                isSaved = true,
                isSample = true,
            ),
            NftyMessage(
                id = "msg_008",
                time = now - 52 * hour,
                topic = "iot-telemetry",
                title = "Solar Inverter Grid Feed In: 18.4 kWh Today",
                message = "Peak solar generation recorded at 3.2 kW at 13:15. Battery state of charge (SoC): 98%. Grid net import: 0.0 kWh.",
                priority = NftyPriority.MIN,
                tags = listOf("sunny", "solar", "energy"),
                isRead = true,
                isSaved = false,
                isSample = true,
            ),
        )
    }
}

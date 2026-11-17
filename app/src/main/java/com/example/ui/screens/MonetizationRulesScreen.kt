package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen

private data class EligibilityCriterion(
    val number: Int,
    val title: String,
    val description: String,
    val icon: ImageVector
)

private data class ViolationRule(
    val number: Int,
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val points: List<String>,
    val penalty: String
)

private val AccentRose = Color(0xFFE1306C)
private val AccentCrimson = Color(0xFF9F1239)
private val AccentAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonetizationRulesScreen(
    onNavigateTo: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val eligibilityCriteria = remember {
        listOf(
            EligibilityCriterion(
                number = 1,
                title = "Age Requirement",
                description = "Users must be at least 18 years old to apply for monetization.",
                icon = Icons.Filled.VerifiedUser
            ),
            EligibilityCriterion(
                number = 2,
                title = "Followers Requirement",
                description = "Must have a minimum of 500 followers.",
                icon = Icons.Filled.Group
            ),
            EligibilityCriterion(
                number = 3,
                title = "Views Requirement",
                description = "Must achieve at least 5,000 qualified video views.",
                icon = Icons.Filled.PlayCircle
            ),
            EligibilityCriterion(
                number = 4,
                title = "Community Guidelines",
                description = "The account must not have any active community guidelines violations.",
                icon = Icons.Filled.Shield
            ),
            EligibilityCriterion(
                number = 5,
                title = "Application Process",
                description = "Users can apply for monetization only after meeting all the specified criteria.",
                icon = Icons.Filled.FactCheck
            )
        )
    }

    val violationRules = remember {
        listOf(
            ViolationRule(
                number = 1,
                title = "Sexual Content & Nudity",
                icon = Icons.Filled.Block,
                accentColor = Color(0xFFDC2626),
                points = listOf(
                    "Uploading obscene or pornographic photos and videos is strictly prohibited.",
                    "Videos clearly depicting sexual acts are prohibited.",
                    "Publishing nude photos or videos with sexual intent is banned.",
                    "Sharing any individual's private or intimate photos/videos without their explicit consent is strictly forbidden.",
                    "Uploading child sexual abuse material (CSAM) will lead to an immediate account ban and reporting to the appropriate legal authorities."
                ),
                penalty = "Serious violations will result in an immediate permanent ban along with legal action."
            ),
            ViolationRule(
                number = 2,
                title = "Abusive Language & Harassment",
                icon = Icons.Filled.ReportProblem,
                accentColor = Color(0xFFEA580C),
                points = listOf(
                    "Using extremely obscene or sexually suggestive profanity in videos, comments, or captions is prohibited.",
                    "Insulting, harassing, or threatening other users with abusive language is forbidden.",
                    "Repeatedly targeting any individual with abuse or bullying is strictly banned.",
                    "Hate speech based on race, religion, gender, or identity is prohibited."
                ),
                penalty = "First Offense: Warning or video removal.\nSecond Offense: Temporary suspension.\nRepeated or Severe Violations: Permanent ban."
            ),
            ViolationRule(
                number = 3,
                title = "Fake Views & Spam",
                icon = Icons.Filled.TrendingDown,
                accentColor = Color(0xFFD97706),
                points = listOf(
                    "Purchasing fake followers or fake views is prohibited.",
                    "Using bots to artificially increase likes, views, or comments is banned.",
                    "Creating spam by repeatedly uploading the exact same video is forbidden.",
                    "Misusing the monetization system by creating fake accounts is strictly restricted."
                ),
                penalty = "Monetization suspension, account review, or full account suspension."
            ),
            ViolationRule(
                number = 4,
                title = "Copyright Violation",
                icon = Icons.Filled.Copyright,
                accentColor = Color(0xFFBE123C),
                points = listOf(
                    "Uploading someone else's video as your own is prohibited.",
                    "Using copyrighted movies, music, or videos without proper permission or legal exemptions is banned.",
                    "Stealing content from other creators to gain monetization is strictly forbidden."
                ),
                penalty = "Video removal, copyright strikes, or account suspension for severe or repeated violations."
            )
        )
    }

    Scaffold(
        topBar = { SubScreenTopBar("Monetization Policy & Rules", onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Policy Header Card
                item(key = "policy_hero") {
                    PolicyHeroBanner()
                }

                // Section 1 Header: Monetization Eligibility Rules
                item(key = "eligibility_header") {
                    PolicySectionHeader(
                        stepNumber = "1",
                        title = "Monetization Eligibility Rules",
                        subtitle = "Required milestones before applying to the Creator Program",
                        accentColor = AccentRose
                    )
                }

                // Section 1 Content Card
                item(key = "eligibility_card") {
                    EligibilityRulesCard(criteria = eligibilityCriteria)
                }

                // Section 2 Header: Account Suspension & Violation Rules
                item(key = "violation_header") {
                    Spacer(modifier = Modifier.height(4.dp))
                    PolicySectionHeader(
                        stepNumber = "2",
                        title = "Account Suspension & Violation Rules",
                        subtitle = "Safety, authenticity, and copyright enforcement standards",
                        accentColor = Color(0xFFDC2626)
                    )
                }

                // Enforcement Notice Callout
                item(key = "violation_notice") {
                    Surface(
                        color = Color(0xFFDC2626).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = "Enforcement warning",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Violating any of the following rules on YouSnap may result in video removal, account warnings, temporary suspension, or a permanent ban.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Violation Rule Cards
                itemsIndexed(
                    items = violationRules,
                    key = { _, rule -> "violation_rule_${rule.number}" }
                ) { _, rule ->
                    ViolationRuleCard(rule = rule)
                }

                // General Policy Summary
                item(key = "general_policy_summary") {
                    GeneralPolicySummaryCard()
                }

                // CTA Button to Eligibility Tracker
                item(key = "eligibility_cta") {
                    Button(
                        onClick = { onNavigateTo(Screen.MonetizationScreen) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRose,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("btn_check_eligibility_tracker")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Check Eligibility Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AccentCrimson,
                        AccentRose,
                        Color(0xFFF97316)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.92f, size.height * 0.12f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.12f, size.height * 0.95f)
            )
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Creator Policy & Safety",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "YouSnap Creator Monetization Rules & Guidelines",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 29.sp
            )

            Text(
                text = "Please read and understand the following monetization policies. Violating these rules will result in immediate demonetization or account termination.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                lineHeight = 20.sp
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PolicyHighlightPill("Eligibility Criteria")
                PolicyHighlightPill("Community Safety")
                PolicyHighlightPill("Original Content")
            }
        }
    }
}

@Composable
private fun PolicyHighlightPill(label: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.18f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun PolicySectionHeader(
    stepNumber: String,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun EligibilityRulesCard(criteria: List<EligibilityCriterion>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            criteria.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentRose.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = AccentRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "${item.number}. ${item.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                            lineHeight = 20.sp
                        )
                    }
                }
                if (index < criteria.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViolationRuleCard(rule: ViolationRule) {
    var expanded by remember { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "violation_chevron_rotation"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = rule.accentColor.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("violation_rule_card_${rule.number}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Header Row (>= 48dp touch target)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("violation_rule_toggle_${rule.number}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(rule.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rule.icon,
                        contentDescription = rule.title,
                        tint = rule.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RULE ${rule.number}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = rule.accentColor,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse ${rule.title}" else "Expand ${rule.title}",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

                    rule.points.forEachIndexed { idx, point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Highlighted Penalty Box
                    Surface(
                        color = rule.accentColor.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Penalty",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = rule.accentColor
                            )
                            Text(
                                text = rule.penalty,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralPolicySummaryCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Policy,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "General Policy Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "All YouSnap users must strictly adhere to the Community Guidelines. YouSnap takes strong action against sexual exploitation, pornography, harassment, hate speech, fraud, and other serious violations. Suspension decisions will depend on the severity of the violation, available evidence, and the user's prior infraction history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                lineHeight = 21.sp
            )
        }
    }
}

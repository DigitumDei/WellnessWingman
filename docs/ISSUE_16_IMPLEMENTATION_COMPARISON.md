# Issue #16 vs Current Implementation Comparison

Issue #16: Historical View Navigation with Arrow Controls and Hierarchical Drill-Down

---

## Matches Current Implementation

| Feature | Notes |
|---|---|
| Calendar icon button on main page → Week View | Implemented with `Icons.Default.CalendarViewMonth` in the top app bar |
| Week View with daily entry counts | 7 day sections showing entry type counts per day |
| Year View with 12-month grid | 3x4 grid of months |
| Day Detail View | Implemented, parameterized by `LocalDate` |
| Prev/Next period arrows on all views | All views have prev/next and Today buttons |
| Tap a day in Week View → Day Detail | Implemented |
| Tap a month in Year View → Month View | Implemented (but see known bug below) |
| Tap a day in Month View → Day Detail | Implemented |
| ViewModels for all views | `WeekViewModel`, `CalendarViewModel`, `YearViewModel`, `DayDetailViewModel` all exist |
| Data repositories with date-range queries | `TrackedEntryRepository` and summary repos exist |
| Weekly AI summary card | Implemented in Week View |

---

## Differences from Issue #16

### 1. Month View Layout — Major Difference

**Issue #16 specifies:**
- 4–5 week cards showing: week date range, entry counts, sparklines
- Tapping a week card drills into **Week View** for that week

**Current implementation:**
- Calendar day grid (individual day cells with colored dots)
- Tapping a day goes directly to Day Detail — no intermediate week level

### 2. Time-Scale Navigation Between Views — Not Implemented

**Issue #16 specifies:**
- Navigation arrows at bottom of each view to move up/down the hierarchy
  (e.g., Week View has "← Month View" and "Year View →")

**Current implementation:**
- No cross-scale navigation arrows; each view is accessed independently via the main entry point

### 3. Year View → Month View Navigation — Broken

**Issue #16 specifies:**
- Tapping a month cell in Year View navigates to Month View for that month

**Current implementation:**
- The tap handler exists but navigation is broken — it does not correctly pass the selected month to Month View

### 4. Monthly and Yearly AI Summaries — Not Implemented

**Issue #16 specifies:**
- Monthly summary card in Month View
- Yearly summary card in Year View

**Current implementation:**
- Only the **weekly** AI summary is implemented (in Week View)
- Month View and Year View have no summary cards

### 5. Entry-Type Breakdown Display — Partial

**Issue #16 specifies:**
- Each period card shows counts broken down by entry type (meals, weight, symptoms, etc.) with color coding

**Current implementation:**
- Week View shows counts per day broken down by type
- Month View shows dots per day (less detailed, no explicit counts)
- Year View shows only a single aggregate count per month cell

### 6. Historical Summary Storage — Not Implemented

**Issue #16 links to issue #26 (Summary Generation and Storage):**
- Summaries should be generated and persisted for each period

**Current implementation:**
- Summaries are generated on-demand and not stored/cached

---

## Summary

The navigation skeleton (calendar icon → Week → Month → Year → Day Detail) is in place with ViewModels and repositories wired up. The main gaps are:

1. **Month View** needs to be redesigned from a day-grid to a week-card layout
2. **Cross-scale navigation arrows** (moving up/down the hierarchy from within a view) are missing
3. **Year → Month navigation** is broken
4. **AI summaries** for Month and Year views are not implemented
5. **Summary persistence** (issue #26) is not implemented

using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WellnessWingman.Migrations
{
    /// <inheritdoc />
    public partial class AddCapturedAtTimeZoneMetadata : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "CapturedAtOffsetMinutes",
                table: "TrackedEntries",
                type: "INTEGER",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "CapturedAtTimeZoneId",
                table: "TrackedEntries",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "CapturedAtOffsetMinutes",
                table: "TrackedEntries");

            migrationBuilder.DropColumn(
                name: "CapturedAtTimeZoneId",
                table: "TrackedEntries");
        }
    }
}

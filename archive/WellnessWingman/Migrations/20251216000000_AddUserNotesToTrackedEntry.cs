using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WellnessWingman.Migrations
{
    /// <inheritdoc />
    public partial class AddUserNotesToTrackedEntry : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "UserNotes",
                table: "TrackedEntries",
                type: "TEXT",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "UserNotes",
                table: "TrackedEntries");
        }
    }
}

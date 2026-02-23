
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace WellnessWingman.Data;

public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<WellnessWingmanDbContext>
{
    public WellnessWingmanDbContext CreateDbContext(string[] args)
    {
        var optionsBuilder = new DbContextOptionsBuilder<WellnessWingmanDbContext>();
        // Use a dummy database name for design-time purposes.
        optionsBuilder.UseSqlite("Data Source=wellnesswingman_design.db3");

        return new WellnessWingmanDbContext(optionsBuilder.Options);
    }
}

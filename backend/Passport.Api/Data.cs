using Microsoft.EntityFrameworkCore;
namespace Passport.Api;
public abstract class Entity
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletedAt { get; set; }
}
public sealed class Asset : Entity
{
    public string OwnerUid { get; set; } = "";
    public Guid? HouseholdId { get; set; }
    public string Name { get; set; } = "";
    public string Brand { get; set; } = "";
    public string Category { get; set; } = "Other";
    public string Model { get; set; } = "";
    public string Serial { get; set; } = "";
    public string Vendor { get; set; } = "";
    public string Notes { get; set; } = "";
    public string Currency { get; set; } = "USD";
    public decimal Price { get; set; }
    public DateOnly? PurchaseDate { get; set; }
    public DateOnly? WarrantyEnd { get; set; }
    public DateOnly? ReturnEnd { get; set; }
    public int Version { get; set; } = 1;
}
public sealed class AssetEvent : Entity { public Guid AssetId { get; set; } public string Kind { get; set; } = ""; }
public sealed class PassportDb(DbContextOptions<PassportDb> options) : DbContext(options)
{
    public DbSet<Asset> Assets => Set<Asset>();
    public DbSet<AssetEvent> Events => Set<AssetEvent>();
    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<Asset>().HasIndex(x => new { x.OwnerUid, x.DeletedAt, x.CreatedAt });
        b.Entity<Asset>().Property(x => x.Version).IsConcurrencyToken();
        b.Entity<Asset>().Property(x => x.Price).HasPrecision(18, 2);
        b.Entity<Asset>().Property(x => x.OwnerUid).HasMaxLength(128);
        b.Entity<Asset>().Property(x => x.Name).HasMaxLength(160);
        b.Entity<AssetEvent>().HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId);
    }
}
public sealed record AssetInput(string Name, string Category, string Currency, decimal Price, string Brand = "", string Model = "", string Serial = "", string Vendor = "", string Notes = "", DateOnly? PurchaseDate = null, DateOnly? WarrantyEnd = null, DateOnly? ReturnEnd = null, int Version = 1)
{
    public static readonly string[] Currencies = ["USD", "EUR", "GBP", "AUD", "CAD", "SGD", "IDR", "JPY"];
    public Dictionary<string, string[]> Validate()
    {
        var errors = new Dictionary<string, string[]>();
        if (string.IsNullOrWhiteSpace(Name) || Name.Length > 160) errors["name"] = ["Enter an asset name (1–160 characters)."];
        if (string.IsNullOrWhiteSpace(Category) || Category.Length > 80) errors["category"] = ["Choose a category."];
        if (!Currencies.Contains(Currency)) errors["currency"] = ["Unsupported currency."];
        if (Price < 0 || Price > 999999999999m || decimal.Round(Price, 2) != Price) errors["price"] = ["Use a positive amount with at most two decimal places."];
        if (new[] { Brand, Model, Serial, Vendor }.Any(s => s == null || s.Length > 200) || Notes == null || Notes.Length > 4000) errors["details"] = ["One or more fields are too long."];
        if (PurchaseDate.HasValue && (WarrantyEnd < PurchaseDate || ReturnEnd < PurchaseDate)) errors["dates"] = ["Deadlines cannot precede purchase."];
        return errors;
    }
    public void Apply(Asset a) { a.Name = Name.Trim(); a.Category = Category; a.Currency = Currency; a.Price = Price; a.Brand = Brand; a.Model = Model; a.Serial = Serial; a.Vendor = Vendor; a.Notes = Notes; a.PurchaseDate = PurchaseDate; a.WarrantyEnd = WarrantyEnd; a.ReturnEnd = ReturnEnd; }
}
public sealed record AssetResponse(Guid Id, string Name, string Category, string Currency, decimal Price, string Brand, string Model, string Serial, string Vendor, string Notes, DateOnly? PurchaseDate, DateOnly? WarrantyEnd, DateOnly? ReturnEnd, int Version, DateTimeOffset CreatedAt, DateTimeOffset UpdatedAt)
{
    public static AssetResponse From(Asset a) => new(a.Id, a.Name, a.Category, a.Currency, a.Price, a.Brand, a.Model, a.Serial, a.Vendor, a.Notes, a.PurchaseDate, a.WarrantyEnd, a.ReturnEnd, a.Version, a.CreatedAt, a.UpdatedAt);
}

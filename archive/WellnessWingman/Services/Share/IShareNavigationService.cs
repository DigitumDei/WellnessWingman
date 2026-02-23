namespace WellnessWingman.Services.Share;

public interface IShareNavigationService
{
    Task PresentShareDraftAsync(Guid draftId, CancellationToken cancellationToken = default);
}

namespace WellnessWingman.Services.Share;

public interface ISharedImageDraftStore
{
    void AddOrReplace(SharedImageDraft draft);
    SharedImageDraft? Get(Guid draftId);
    void Remove(Guid draftId);
}

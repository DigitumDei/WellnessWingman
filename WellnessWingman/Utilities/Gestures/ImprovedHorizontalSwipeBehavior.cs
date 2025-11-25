using System.Windows.Input;

namespace WellnessWingman.Utilities.Gestures;

public class ImprovedHorizontalSwipeBehavior : Behavior<View>
{
    private enum GestureState
    {
        None,
        Observing,     // Initial state - watching but not interfering
        Horizontal,    // Committed to horizontal gesture
        Vertical,      // Cancelled - vertical gesture detected
        Cancelled
    }

    public static readonly BindableProperty SwipeLeftCommandProperty =
        BindableProperty.Create(nameof(SwipeLeftCommand), typeof(ICommand), typeof(ImprovedHorizontalSwipeBehavior));

    public static readonly BindableProperty SwipeLeftCommandParameterProperty =
        BindableProperty.Create(nameof(SwipeLeftCommandParameter), typeof(object), typeof(ImprovedHorizontalSwipeBehavior));

    public static readonly BindableProperty SwipeRightCommandProperty =
        BindableProperty.Create(nameof(SwipeRightCommand), typeof(ICommand), typeof(ImprovedHorizontalSwipeBehavior));

    public static readonly BindableProperty SwipeRightCommandParameterProperty =
        BindableProperty.Create(nameof(SwipeRightCommandParameter), typeof(object), typeof(ImprovedHorizontalSwipeBehavior));

    private PanGestureRecognizer? _panGestureRecognizer;
    private View? _associatedView;
    private GestureState _gestureState;
    private double _totalX;
    private double _totalY;

    // Conservative thresholds to avoid scroll interference
    public double SwipeThreshold { get; set; } = 120;
    public double VerticalTolerance { get; set; } = 40;
    public double HorizontalThreshold { get; set; } = 30;
    public double FeedbackTranslationRatio { get; set; } = 0.15;
    public double MaxFeedbackTranslation { get; set; } = 32;

    public ICommand? SwipeLeftCommand
    {
        get => (ICommand?)GetValue(SwipeLeftCommandProperty);
        set => SetValue(SwipeLeftCommandProperty, value);
    }

    public object? SwipeLeftCommandParameter
    {
        get => GetValue(SwipeLeftCommandParameterProperty);
        set => SetValue(SwipeLeftCommandParameterProperty, value);
    }

    public ICommand? SwipeRightCommand
    {
        get => (ICommand?)GetValue(SwipeRightCommandProperty);
        set => SetValue(SwipeRightCommandProperty, value);
    }

    public object? SwipeRightCommandParameter
    {
        get => GetValue(SwipeRightCommandParameterProperty);
        set => SetValue(SwipeRightCommandParameterProperty, value);
    }

    protected override void OnAttachedTo(View bindable)
    {
        base.OnAttachedTo(bindable);

        bindable.BindingContextChanged += OnBindableBindingContextChanged;
        BindingContext = bindable.BindingContext;
        _associatedView = bindable;
        _panGestureRecognizer = new PanGestureRecognizer();
        _panGestureRecognizer.PanUpdated += OnPanUpdated;
        bindable.GestureRecognizers.Add(_panGestureRecognizer);
    }

    protected override void OnDetachingFrom(View bindable)
    {
        base.OnDetachingFrom(bindable);

        if (_panGestureRecognizer is not null)
        {
            _panGestureRecognizer.PanUpdated -= OnPanUpdated;
            bindable.GestureRecognizers.Remove(_panGestureRecognizer);
            _panGestureRecognizer = null;
        }

        bindable.BindingContextChanged -= OnBindableBindingContextChanged;
        _associatedView = null;
        BindingContext = null;
    }

    private void OnPanUpdated(object? sender, PanUpdatedEventArgs e)
    {
        if (_associatedView is not View view)
        {
            return;
        }

        switch (e.StatusType)
        {
            case GestureStatus.Started:
                _gestureState = GestureState.Observing;
                _totalX = 0;
                _totalY = 0;
                break;

            case GestureStatus.Running:
                HandleRunningGesture(view, e);
                break;

            case GestureStatus.Canceled:
                _gestureState = GestureState.Cancelled;
                _ = ResetViewAsync(view);
                break;

            case GestureStatus.Completed:
                HandleSwipeCompletion(view);
                break;
        }
    }

    private void HandleRunningGesture(View view, PanUpdatedEventArgs e)
    {
        _totalX = e.TotalX;
        _totalY = e.TotalY;

        switch (_gestureState)
        {
            case GestureState.Observing:
                // Check if this is clearly a vertical gesture
                if (Math.Abs(_totalY) > VerticalTolerance)
                {
                    _gestureState = GestureState.Vertical;
                    _ = ResetViewAsync(view);
                    return;
                }

                // Check if this is clearly a horizontal gesture
                if (Math.Abs(_totalX) > HorizontalThreshold &&
                    Math.Abs(_totalX) > Math.Abs(_totalY) + VerticalTolerance)
                {
                    _gestureState = GestureState.Horizontal;
                    // Now we can start applying visual feedback
                    ApplyVisualFeedback(view);
                }
                // Otherwise, stay in Observing state and let ScrollView handle naturally
                break;

            case GestureState.Horizontal:
                // Check if user changed their mind to vertical
                if (Math.Abs(_totalY) > Math.Abs(_totalX) + VerticalTolerance)
                {
                    _gestureState = GestureState.Cancelled;
                    _ = ResetViewAsync(view);
                    return;
                }

                // Continue applying visual feedback
                ApplyVisualFeedback(view);
                break;

            case GestureState.Vertical:
            case GestureState.Cancelled:
                // Do nothing - gesture is cancelled
                break;
        }
    }

    private void ApplyVisualFeedback(View view)
    {
        var translation = Math.Clamp(_totalX * FeedbackTranslationRatio, -MaxFeedbackTranslation, MaxFeedbackTranslation);
        view.TranslationX = translation;
    }

    private async void HandleSwipeCompletion(View view)
    {
        try
        {
            if (_gestureState == GestureState.Horizontal &&
                Math.Abs(_totalX) > SwipeThreshold &&
                Math.Abs(_totalX) > Math.Abs(_totalY))
            {
                if (_totalX > 0)
                {
                    ExecuteCommand(SwipeRightCommand, SwipeRightCommandParameter);
                }
                else
                {
                    ExecuteCommand(SwipeLeftCommand, SwipeLeftCommandParameter);
                }
            }
        }
        finally
        {
            _gestureState = GestureState.None;
            await ResetViewAsync(view).ConfigureAwait(false);
        }
    }

    private static void ExecuteCommand(ICommand? command, object? parameter)
    {
        if (command is null)
        {
            return;
        }

        if (command.CanExecute(parameter))
        {
            command.Execute(parameter);
        }
    }

    private static async Task ResetViewAsync(View view)
    {
        if (Math.Abs(view.TranslationX) < 1)
        {
            view.TranslationX = 0;
            return;
        }

        await view.TranslateToAsync(0, 0, 120, Easing.SinOut).ConfigureAwait(false);
        view.TranslationX = 0;
    }

    private void OnBindableBindingContextChanged(object? sender, EventArgs e)
    {
        if (sender is BindableObject bindable)
        {
            BindingContext = bindable.BindingContext;
        }
    }
}

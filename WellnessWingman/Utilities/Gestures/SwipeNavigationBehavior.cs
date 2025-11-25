using System.Windows.Input;

namespace WellnessWingman.Utilities.Gestures;

public class SwipeNavigationBehavior : Behavior<View>
{
    public static readonly BindableProperty SwipeLeftCommandProperty =
        BindableProperty.Create(nameof(SwipeLeftCommand), typeof(ICommand), typeof(SwipeNavigationBehavior));

    public static readonly BindableProperty SwipeRightCommandProperty =
        BindableProperty.Create(nameof(SwipeRightCommand), typeof(ICommand), typeof(SwipeNavigationBehavior));

    private SwipeGestureRecognizer? _leftSwipeGesture;
    private SwipeGestureRecognizer? _rightSwipeGesture;

    public ICommand? SwipeLeftCommand
    {
        get => (ICommand?)GetValue(SwipeLeftCommandProperty);
        set => SetValue(SwipeLeftCommandProperty, value);
    }

    public ICommand? SwipeRightCommand
    {
        get => (ICommand?)GetValue(SwipeRightCommandProperty);
        set => SetValue(SwipeRightCommandProperty, value);
    }

    protected override void OnAttachedTo(View bindable)
    {
        base.OnAttachedTo(bindable);

        _leftSwipeGesture = new SwipeGestureRecognizer { Direction = SwipeDirection.Left };
        _leftSwipeGesture.Swiped += OnSwiped;
        bindable.GestureRecognizers.Add(_leftSwipeGesture);

        _rightSwipeGesture = new SwipeGestureRecognizer { Direction = SwipeDirection.Right };
        _rightSwipeGesture.Swiped += OnSwiped;
        bindable.GestureRecognizers.Add(_rightSwipeGesture);
    }

    protected override void OnDetachingFrom(View bindable)
    {
        base.OnDetachingFrom(bindable);

        if (_leftSwipeGesture is not null)
        {
            _leftSwipeGesture.Swiped -= OnSwiped;
            bindable.GestureRecognizers.Remove(_leftSwipeGesture);
            _leftSwipeGesture = null;
        }

        if (_rightSwipeGesture is not null)
        {
            _rightSwipeGesture.Swiped -= OnSwiped;
            bindable.GestureRecognizers.Remove(_rightSwipeGesture);
            _rightSwipeGesture = null;
        }
    }

    private void OnSwiped(object? sender, SwipedEventArgs e)
    {
        switch (e.Direction)
        {
            case SwipeDirection.Left:
                ExecuteCommand(SwipeLeftCommand);
                break;
            case SwipeDirection.Right:
                ExecuteCommand(SwipeRightCommand);
                break;
        }
    }

    private static void ExecuteCommand(ICommand? command)
    {
        if (command is not null && command.CanExecute(null))
        {
            command.Execute(null);
        }
    }
}

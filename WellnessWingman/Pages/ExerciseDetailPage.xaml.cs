using WellnessWingman.PageModels;
using Microsoft.Maui.Controls;

namespace WellnessWingman.Pages;

public partial class ExerciseDetailPage : ContentPage
{
    public ExerciseDetailPage(ExerciseDetailViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}

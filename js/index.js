(function($) {

  $('a[href^="#"]').on('click', function(e) {
    e.preventDefault();

    var target = this.hash;
    var $target = $(target);

    $('.mdl-layout__content').stop().animate({
      'scrollTop': $target.offset().top
    }, 1000, 'swing', function() {
      window.location.hash = target;
    });
  });

})(jQuery);
